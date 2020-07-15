/**
 * This class represents a connection manager. It includes logic
 * for sending and receiving data.
 * 
 * @author Soumyakanti Das
 * 
 */

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

public class Manager extends Thread {

    public static int window = 65000;
    private int dataChunk = 100000000; // 10^8 ~= 100 MB

    private static InetAddress sendIP;
    private int port = 64000;
    private DataSender ds;
    private DataReceiver dr;
    public static boolean sender = false;
    private volatile byte[] data;
    private volatile byte[] received;
    private String fileWritePath;
    private String fileReadPath;

    private static Queue<byte[]> queue;
    private static final Object lock = new Object();
    private static HashSet<Integer> expectedSequenceNumbers;
    private static HashSet<Integer> receivedSequences;
    public static boolean connectionAcked = false;
    private boolean sendingHeartBeat = false;

    private int expectedSeqNum = 0;
    public volatile static boolean connectionClosed = false;
    private boolean isBufferCreated = false;
    private static boolean startedReceivingData = false;
    private volatile boolean receivedAckForClose = false;
    private volatile int nakCount = 1;

    public static int timeout = 100000;
    private volatile long elapsedTime = -1;
    private long receivedLength = 0;
    private static volatile long lastReceivedTime = System.currentTimeMillis();
    public static long lastSentTime;
    private volatile boolean receivedStart = false;
    private volatile boolean closedOrContinued = false;

    private volatile int iterations = 0;

    private InputStream in;

    // private int src;
    private int dest;

    public Manager() {
        this.setName("Manager-thread");
        ds = new DataSender();
        dr = new DataReceiver(port);
        queue = new ArrayDeque<>();
        expectedSequenceNumbers = new HashSet<>();
        receivedSequences = new HashSet<>();
        dr.start();
    }

    public static void reset() {
        synchronized(lock) {
            queue = new ArrayDeque<>();
            expectedSequenceNumbers = new HashSet<>();
            receivedSequences = new HashSet<>();
        }
        connectionAcked = false;
        connectionClosed = false;
        startedReceivingData = false;
        timeout = 10000;
        lastReceivedTime = System.currentTimeMillis();
        // lastSentTime = null;
    }

    public void run() {
        try {
            process();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Acquires lock, and adds packet to the queue. Also adds seq number to the
     * receivedSequences Set, if it's a data packet.
     * 
     * @param data the received packet
     */
    public static void enqueue(byte[] data) {
        synchronized (lock) {
            queue.add(data);
            if (Packet.isDataPacket(data)) {
                receivedSequences.add(Packet.getSeq(data));
                startedReceivingData = true;
                lastReceivedTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * sets sender's IP
     * 
     * @param ip sender's IP.
     */
    public static void setIP(InetAddress ip) {
        if (sendIP == null)
            sendIP = ip;
    }

    /**
     * Processes the received packets
     * 
     * @throws InterruptedException
     */
    private void process() throws InterruptedException {
        while (sender ? (connectionClosed ? !isTimeOut() : true) : !isTimeOut()) {
            byte[] packet = null;

            // remove a packet from the queue, if not empty
            synchronized (lock) {
                if (!queue.isEmpty()) {
                    packet = queue.poll();
                }
            }

            // if packet was found in the queue
            if (packet != null) {
                int seq = Packet.getSeq(packet);
                int len = Packet.getLen(packet);
                int srcID = Packet.getSrcID(packet);
                int destID = Packet.getDestID(packet);

                if (destID != Rover.selfID) {
                    // System.out.printf("Routing packet with seq %d from %d to %d \n", seq, srcID, destID);
                    ds.send(packet, destID, port);
                    continue;
                }

                // sender operations
                if (sender) {
                    // if heartbeat received, do nothing
                    if (Packet.isHeartBeat(packet)) {
                        System.out.println("received heartbeat");
                        continue;
                    }
                    // if ACK received from receiver - connection established
                    if (Packet.isAck(packet) && seq == 0) {
                        System.out.println("Received ACK for start");
                        connectionAcked = true;

                        // create a thread to send all data
                        new Thread("Data-sender-thread") {
                            public void run() {
                                whenConnEstablishedSendData();
                            }
                        }.start();

                    } else if (Packet.isNak(packet) && len == iterations) { // if NAK received for some sequence
                        sendNakData(seq);
                    } else if (!closedOrContinued && Packet.isClose(packet) && seq >= data.length && len == iterations) { // if receiver closes the connection
                        closedOrContinued = true;
                        closeOrContinue();
                    }

                    // receiver operations
                } else {

                    // when started receiving data, start sending heartbeats
                    if (!sendingHeartBeat && startedReceivingData) {
                        sendHeartBeat(srcID);
                    }

                    // if new connection request is made
                    if (!receivedStart && Packet.isStart(packet)) {
                        receivedStart = true;

                        // If receivedStart and recieved array already full, write to file
                        if (received != null && expectedSeqNum >= received.length) {
                            writeFile();
                            reset();
                            isBufferCreated = false;
                            expectedSeqNum = 0;
                        }

                        // send ACK to sender and begin receiving data
                        if (!startedReceivingData)
                            whenReceivedStartSendAck(srcID);

                        if (!isBufferCreated) {
                            iterations++;
                            System.out.println("Creating buffer of length: " + len + ", iter: " + iterations);
                            received = new byte[len];
                            receivedLength += len;
                            // extract the name of the file
                            fileWritePath = new String(packet, 10, packet.length - 10);

                            // save expected sequence numbers
                            for (int i = seq; i < len; i += Manager.window) {
                                expectedSequenceNumbers.add(i);
                            }

                            isBufferCreated = true;

                            // store starting time
                            if (elapsedTime == -1)
                            elapsedTime = System.currentTimeMillis();
                        }

                        // if connection is open and a data packet is received, process the packet
                    } else if (!connectionClosed && Packet.isDataPacket(packet)) {
                        processReceivedData(packet, seq, len, srcID);
                        // System.out.println("Received data: " + seq + ", " + len);
                        receivedStart = false;

                        // when ACK is received from sender on closing connection
                    } else if (Packet.isClose(packet) && Packet.isAck(packet)) {
                        receivedAckForClose = true;
                        connectionClosed = true;
                        System.out.println("Closing connection and saving file");
                        // calculate elapsed time
                        elapsedTime = System.currentTimeMillis() - elapsedTime;
                        writeFile();
                        printStats();
                    }
                }
            }
        }
    }

    /**
     * Initializes connection and sends a file to a receiver.
     * 
     * @param ip       sender's IP
     * @param filePath path of the file to send
     */
    public void sendFile(String filePath, int destID) {
        sender = true;
        fileReadPath = filePath;
        try {
            in = new FileInputStream(filePath);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }
        this.dest = destID;
        // read the file and store it in data
        this.data = readFile();
        System.out.println("Sending file of length: " + data.length);

        // make a packet with start flag as true. Store the length of the file
        // to be sent in the len field, and include the file name as data
        byte[] start = Packet.makePacket(0, data.length, true, false, false, false, filePath.getBytes(),
         0, Rover.selfID, destID);

        startConnection(start);
    }

    /**
     * Starts the connection by sending the start packet
     * 
     * @param start packet to start connection
     */
    private void startConnection(byte[] start) {
        while (!isTimeOut() && !connectionAcked) {
            System.out.println("Sending Start packet of length: " + Packet.getLen(start) + ", " + iterations);
            ds.send(start, dest, port);
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends data to the receiver by dividing it into packets of length of atmost
     * window size
     * 
     */
    private void whenConnEstablishedSendData() {
        System.out.println("Sending data...");
        int len = Manager.window;
        int offset = 0;
        while (offset < data.length) {
            if (offset + len > data.length)
                len = data.length - offset;
            byte[] packet = Packet.makePacket(offset, len, false, false, false, false, data, offset,
             Rover.selfID, dest);
            ds.send(packet, dest, port);

            // Wait for a small amount of time between sending packets.
            // Doing this doesn't swamp the receiver and less packets are
            // dropped, increasing efficiency.
            try {
                Thread.sleep(5);
            } catch (Exception e) {
                e.printStackTrace();
            }
            offset += len;
        }
    }

    /**
     * Send NAKed data
     * 
     * @param seq sequence number of the packet to be sent
     */
    private void sendNakData(int seq) {
        int len = Manager.window;
        if (seq + len > data.length)
            len = data.length - seq;
        System.out.println("Sending NAKed Data for seq: " + seq + " len: " + len);
        byte[] packet = Packet.makePacket(seq, len, false, false, false, false, data, seq, Rover.selfID, dest);
        ds.send(packet, dest, port);
    }

    private void closeOrContinue() {
        this.data = readFile();
        if (data.length <= 0) {
            System.out.println("File sent, closing connection");
            connectionClosed = true;
            sendCloseAck();
        } else {
            System.out.println("Reseting and sending start packet");
            reset();

            byte[] start = Packet.makePacket(0, data.length, true, false, false, false, fileReadPath.getBytes(),
            0, Rover.selfID, dest);

            new Thread("Connection-starting-thread") {
                public void run() {
                    startConnection(start);
                    closedOrContinued = false;
                }
            }.start();
        }
    }

    /**
     * Send ACK for Close to Receiver
     */
    private void sendCloseAck() {
        System.out.println("Acknowledging Close");
        printStats();
        byte[] packet = Packet.makePacket(0, 0, false, true, true, false, new byte[] {}, 0, Rover.selfID, dest);
        ds.send(packet, dest, port);
    }


    /**
     * Sends heartbeat to the receiver in regular intervals
     * 
     */
    private void sendHeartBeat(int dest) {
        sendingHeartBeat = true;
        new Thread("Heartbeat-sending-thread") {
            public void run() {
                while (!connectionClosed) {
                    System.out.println("Sending heartbeat");
                    byte[] packet = Packet.makePacket(0, 0, true, true, true, true, new byte[] {}, 0, Rover.selfID, dest);

                    ds.send(packet, dest, port);

                    try {
                        Thread.sleep(timeout / 4);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * Send ACK on start
     */
    private void whenReceivedStartSendAck(int dest) {
        byte[] ack = Packet.makePacket(0, 0, false, false, true, false, new byte[] {}, 0, Rover.selfID, dest);
        System.out.println("Sending ACK on start to " + Rover.externalIP.get(Rover.nextHop.get(dest)));
        ds.send(ack, dest, port);
    }

    /**
     * Send NAK packet
     * 
     * @param seq sequence number of the packet
     */
    private void sendNak(int seq, int dest, int num) {
        byte[] packet = Packet.makePacket(seq, num, false, false, false, true, new byte[] {}, 0, Rover.selfID, dest);
        ds.send(packet, dest, port);
    }

    /**
     * Process the received data packet
     * 
     * @param packet received packet
     * @param seq    sequence number of the packet
     * @param len    length of the packet
     */
    private void processReceivedData(byte[] packet, int seq, int len, int dest) {
        // if the received seq number is what the receiver expects, copy to the buffer
        if (seq == expectedSeqNum) {
            for (int i = 0; i < len; i++) {
                received[seq + i] = packet[10 + i];
            }

            // remove the sequence number from expected set
            expectedSequenceNumbers.remove(seq);

            // compute the next expected seq number - the next seq number
            // which hasn't been received
            while (!expectedSequenceNumbers.contains(expectedSeqNum) && expectedSeqNum < received.length) {
                expectedSeqNum += len;
            }
            // System.out.println("Next expected seq: " + expectedSeqNum);

            // send NAK if the expected seq hasn't been received yet
            sendNakIfSeqNotInReceivedSequences(expectedSeqNum, dest, iterations);

            // if received seq is less than expected, ignore it, as it's duplicate
        } else if (seq < expectedSeqNum) {
            // System.out.println("Ignoring seq: " + seq + " and len: " + len);

            // if received seq is greater than expected, copy it but send NAK on
            // expected seq.
        } else {
            sendNakIfSeqNotInReceivedSequences(expectedSeqNum, dest, iterations);
            // System.out.println("Copying data from seq: " + seq + " and len: " + len);

            // copy the received data
            if (expectedSequenceNumbers.contains(seq)) {
                for (int i = 0; i < len; i++) {
                    received[seq + i] = packet[10 + i];
                }
                expectedSequenceNumbers.remove(seq);
            }
        }

        // close when all packets have been received
        if (expectedSeqNum >= received.length) {
            new Thread("Connection-closing-Thread") {
                public void run() {
                    close(expectedSeqNum, dest);
                }
            }.start();
        }
    }

    /**
     * Closes the connection with the sender and saves file
     * 
     * @param seq sequence number
     */
    private void close(int seq, int dest) {
        System.out.println("Sending Close");
        
        byte[] packet = Packet.makePacket(seq, iterations, false, true, false, false, new byte[] {}, 0, Rover.selfID, dest);

        new Thread("Close-sender-thread") {
            public void run() {
                while (iterations == Packet.getLen(packet) && !receivedAckForClose && !receivedStart) {
                    ds.send(packet, dest, port);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }


    /**
     * Send NAK packets if sequence number is missing
     * 
     * @param seq missing sequence numbers
     */
    private void sendNakIfSeqNotInReceivedSequences(int seq, int dest, int num) {

        // creates a new thread to send NAKs
        new Thread("NAK-sender-thread") {

            public void run() {
                boolean seqInReceived = false;
                while (!connectionClosed && !seqInReceived && expectedSequenceNumbers.contains(seq)) {
                    // Sleep for a small amount of time. By waiting, the Receiver gets
                    // the chance to maybe receive and put the required packet into 
                    // the queue before NAK is sent. This saves a lot of NAKs.
                    int t = Math.min(100*nakCount, 5000);
                    try {
                        Thread.sleep(t);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // finally check if the seq has been received during sleep().
                    synchronized(lock){
                        seqInReceived = receivedSequences.contains(seq);
                    }

                    // if seq hasn't been received, send NAK
                    if (!seqInReceived) {
                        System.out.println("Sending NAK for seq:" + seq + " after sleeping " + t + " ms, " + num);
                        sendNak(seq, dest, num);
                        nakCount++;
                    }
                }
            }
        }.start();

        nakCount = 1;
    }


    /**
     * Read file from the filePath and return bytes.
     * 
     * @param filePath path of the file
     * @return the file as arrays of bytes
     */
    private byte[] readFile() {
        iterations++;
        int buffLength = dataChunk;
        byte [] buffer = new byte[buffLength];

        try {
            int len = in.read(buffer, 0, buffLength);
            if (len > 0) buffer = Arrays.copyOfRange(buffer, 0, len);
            else buffer = new byte[]{};
        } catch (Exception e) {
            e.printStackTrace();
        }

        return buffer;
    }


    /**
     * Write the received file.
     */
    private void writeFile() {
        String fileName = fileWritePath.split("\\.")[0] + "-received." + fileWritePath.split("\\.")[1];
        Path curr = Paths.get("").toAbsolutePath();
        Path filePath = Paths.get(curr.toString(), fileName.trim());
        File file = new File(filePath.toString());

        try(OutputStream out = new FileOutputStream(file, true)) {
            file.createNewFile();
            out.write(received);
            out.flush();
            System.out.println("File saved to: " + file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static boolean isTimeOut() {
        if (System.currentTimeMillis() - lastReceivedTime > timeout) {
            if (connectionClosed) {
                System.out.println("Closing " + Thread.currentThread().getName());
            } else {
                System.out.println(Thread.currentThread().getName() + " Timeout!");
            }
            connectionClosed = true;
            return true;
        }

        return false;
    }


    /**
     * Prints some stats
     */
    public void printStats() {
        if (!sender) {
            System.out.println("Time taken to transfer: " + elapsedTime);
            System.out.println("Throughput achieved: " + (receivedLength/(double)elapsedTime) + " KB/s");
        }
    }
}