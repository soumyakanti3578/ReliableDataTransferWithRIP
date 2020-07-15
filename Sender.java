/**
 * This class represents a Sender, which logically resides inside a Router.
 * It multicasts a Rover's vectors in RIPv2 format.
 * 
 * @author Soumyakanti Das
 */

import java.util.HashMap;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class Sender extends Thread{
    private HashMap<Integer, HashMap<Integer, Integer>> updates = new HashMap<>();
    private HashMap<Integer, Integer> defaultMap = new HashMap<>();
    private HashMap<Integer, HashMap<Integer, Integer>> table;
    private int id;
    private HashMap<Integer, Integer> nextHop;
    private InetAddress group;
    private int port;

    public Sender(int id, int port, InetAddress group,
     HashMap<Integer, HashMap<Integer, Integer>> table,
     HashMap<Integer, Integer> nextHop) {
        this.id = id;
        this.group = group;
        this.port = port;
        this.table = table;
        this.nextHop = nextHop;
    }

    public void run() {
        while (true) {
            try {
                send();
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
        }
        }
    }

    /**
     * Multicasts a RIPv2 packet.
     * 
     * @throws Exception
     */
    private void send() throws Exception {
        defaultMap.put(id, 0);
        DatagramSocket socket = new DatagramSocket();
        updates.put(id, table.getOrDefault(id, defaultMap));
        // System.out.println("Rover " + id + " sending update " + updates);
        byte [] msg = getBytes(updates.get(id));
        DatagramPacket packet = new DatagramPacket(msg, msg.length, group, port);

        socket.send(packet);
        socket.close();

        defaultMap = new HashMap<>();
        updates = new HashMap<>();
    }

    /**
     * This method takes a vector, and returns a byte array in RIPv2 format.
     * 
     * @param vector HashMap representing a vector
     * @return byte []
     */
    public byte[] getBytes(HashMap<Integer, Integer> vector) {
        byte [] result = null;
        int size = 4 + 20*vector.size();
        result = new byte[size];
        result[0] = 2;
        result[1] = 2;
        result[2] = 0;
        result[3] = 0;
        
        int index = 4;
        for (int key: vector.keySet()) {
            result[index] = 0;
            result[index+1] = 2;
            result[index+2] = 0;
            result[index+3] = 0;

            result[index+4] = 10;
            result[index+5] = 0;
            result[index+6] = (byte)key;
            result[index+7] = 0;

            result[index+8] = (byte)255;
            result[index+9] = (byte)255;
            result[index+10] = (byte)255;
            result[index+11] = 0;

            result[index+12] = 10;
            result[index+13] = 0;
            int nextHopNode = nextHop.getOrDefault(key, id);
            result[index+14] = (byte)nextHopNode; //id
            result[index+15] = 0;

            result[index+16] = 0;
            result[index+17] = 0;
            result[index+18] = 0;
            result[index+19] = (byte)(int)vector.get(key);

            index += 20;
        }

        return result;
    }
}