/**
 * This class models a Packet of the reliable data transfer protocol
 * 
 * @author Soumyakanti Das
 * 
 */
public class Packet {
    
    public static byte[] makePacket(int seq, int len, boolean start, 
    boolean close, boolean ack, boolean nak, byte[] data, int offset,
    int src, int dest) { 
        int packetSize = len;
        if (start) {
            packetSize = data.length;
        }
        if (close || nak) {
            packetSize = 0;
        }
        byte [] packet = new byte[10 + packetSize];
        
        packet[0] = (byte)(seq >>> 24);
        packet[1] = (byte)(seq >>> 16);
        packet[2] = (byte)(seq >>> 8);
        packet[3] = (byte)(seq);

        packet[4] = (byte)(len >>> 24);
        packet[5] = (byte)(len >>> 16);
        packet[6] = (byte)(len >>> 8);
        packet[7] = (byte)(len);

        int scan = 0;
        if (start) scan |= (1 << 3);
        if (close) scan |= (1 << 2);
        if (ack)   scan |= (1 << 1);
        if (nak)   scan |= 1;

        packet[8] = (byte)(scan << 4);

        int srcDest = (src << 4) | dest;
        packet[9] = (byte)srcDest;

        for (int i = 0; i < packetSize; i++) {
            packet[10+i] = data[offset+i];
        }

        return packet;
    }

    public static boolean isStart(byte[] packet) {
        int mask = 1 << 7;
        return (packet[8] & mask) == mask;
    }

    public static boolean isClose(byte[] packet) {
        int mask = 1 << 6;
        return (packet[8] & mask) == mask;
    }

    public static boolean isAck(byte[] packet) {
        int mask = 1 << 5;
        return (packet[8] & mask) == mask;
    }

    public static boolean isNak(byte[] packet) {
        int mask = 1 << 4;
        return (packet[8] & mask) == mask;
    }

    public static boolean isDataPacket(byte[] packet) {
        return !isStart(packet) && !isClose(packet) &&
               !isAck(packet) && !isNak(packet);
    }

    public static boolean isHeartBeat(byte[] packet) {
        return isStart(packet) && isClose(packet) &&
               isAck(packet) && isNak(packet);
    }

    public static int getSeq(byte[] packet) {
        return  ((packet[0] & 0xFF) << 24) |
                ((packet[1] & 0xFF) << 16) |
                ((packet[2] & 0xFF) << 8)  |
                (packet[3] & 0xFF);
    }

    public static int getLen(byte[] packet) {
        return  ((packet[4] & 0xFF) << 24) |
                ((packet[5] & 0xFF) << 16) |
                ((packet[6] & 0xFF) << 8)  |
                (packet[7] & 0xFF);
    }

    public static int getSrcID(byte[] packet) {
        return packet[9] >>> 4;
    }

    public static int getDestID(byte[] packet) {
        return packet[9] & 0xF;
    }

    public static void putSrcAndDest(byte[] packet, int src, int dest) {
        int srcDest = (src << 4) | dest;
        packet[9] = (byte)srcDest;
    }

    public static String toString(byte[] packet) {
        String result = "Packet[" + "seq: " + Packet.getSeq(packet) +
                        ", len: " + Packet.getLen(packet) + 
                        ", start: " + Packet.isStart(packet) + 
                        ", close: " + Packet.isClose(packet) + 
                        ", Ack: " + Packet.isAck(packet) + 
                        ", Nak: " + Packet.isNak(packet) + "]";

        return result;
    }

    public static void main(String[] args) {
        byte[] start = Packet.makePacket(0, 100, true, false, false, false, "filePath".getBytes(),
         0, 1, 2);

         System.out.println(Packet.getDestID(start));
    }
}