/**
 * This is a Data receiver class. It receives data from 
 * UDP socket, and stores them in a queue
 * 
 * @author Soumyakanti Das
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class DataReceiver extends Thread{

    private int port;
    private volatile int timeout = Manager.timeout;

    public DataReceiver(int port) {
        this.setName("Data-receiver-thread");
        this.port = port;
    }

    public void run() {
        while(Manager.sender? 
                    Manager.connectionAcked ?
                            (Manager.connectionClosed? !Manager.isTimeOut(): true):
                            !Manager.isTimeOut():
                    !Manager.isTimeOut()) 
        {
            receive();
        }
    }

    /**
     * Receive data from socket
     */
    public void receive() {
        byte[] buffer = new byte[Manager.window+10];
        try(DatagramSocket socket = new DatagramSocket(port)) {
            DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);

            socket.setSoTimeout(timeout);
            socket.receive(packet);

            new Thread("Packet-enqueueing-thread"){
                @Override
                public void run() {
                    Manager.enqueue(buffer);

                    // on receiving a packet, store the IP of sender
                    Manager.setIP(packet.getAddress());
                }
            }.start();

        } catch (IOException e) {
            if (Manager.connectionClosed) {
                System.out.println("Closing Receive socket");
            } else {
                System.out.println("receive socket timed out!");
            }
            Manager.connectionClosed = true;
        }
    }
}