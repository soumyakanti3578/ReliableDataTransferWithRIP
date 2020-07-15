/**
 * This class represents a Receiver, which resides in a Router.
 * 
 * @author Soumyakanti Das
 */

import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;


public class Receiver extends Thread {
    private int id;
    private int port;
    private InetAddress group;

    private HashMap<Integer, Long> ttlMap; 
    // copy of the received vector, needed to implement poisoned reverse.
    private HashMap<Integer, Integer> receivedVectorCopy;
    private HashMap<Integer, HashMap<Integer, Integer>> table;
    private HashMap<Integer, Integer> nextHop;
    private HashMap<Integer, String> externalIP;

    private HashMap<Integer, HashMap<Integer, Integer>> tableCopy;

    public Receiver(int id, int port, InetAddress group, 
        HashMap<Integer, HashMap<Integer, Integer>> table,
        HashMap<Integer, Integer> nextHop, 
        HashMap<Integer, Long> ttlMap, 
        HashMap<Integer, String> externalIP) {

        this.id = id;
        this.group = group;
        this.port = port;
        this.table = table;
        this.nextHop = nextHop;
        this.ttlMap = ttlMap;
        this.externalIP = externalIP;
        try {
            this.externalIP.put(id, InetAddress.getLocalHost().getHostAddress());
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        tableCopy = new HashMap<>();
    }

    public void run() {
        try {
            receive();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Receives a RIPv2 packet and updates table.
     * 
     * @throws IOException
     */
    private void receive() throws IOException {
        byte [] buffer = new byte[1024];
        MulticastSocket socket = new MulticastSocket(port);
        socket.joinGroup(group);

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            HashMap<Integer, HashMap<Integer, Integer>> receivedTable = 
                getHashMap(Arrays.copyOfRange(packet.getData(),packet.getOffset(),packet.getLength()));

            int receivedId = (int)receivedTable.keySet().toArray()[0];
            ttlMap.put(receivedId, System.currentTimeMillis());
            HashMap<Integer, Integer> receivedVector = receivedTable.get(receivedId);

            // New connection.
            if (receivedVector.size() == 1) {
                // put value from and to as 1
                receivedVector.put(id, 1);
                receivedVectorCopy.put(id, 1);
            }

            if (receivedId != id) {
                String receivedIP =  packet.getAddress().getHostAddress();
                // System.out.println("Received packet from: " + receivedIP);
                externalIP.put(receivedId, receivedIP);

                // tableHash = table.hashCode();

                // if I receive a packet, then that rover is 1 hop away
                if (table.get(id) != null) table.get(id).put(receivedId, 1);
                table.put(receivedId, receivedVector);

                // if we are receiving updates from a node, then it is one hop away, and next hop is destination
                nextHop.put(receivedId, receivedId);
                // System.out.println("Rover " + id + " received update from " + receivedId + ", " + receivedVector);
                updateSelf(receivedId);

                // System.out.println("############################" + table);

                // LOOK INTO HASHING!!
                if (!tableCopy.equals(table)) { //table.hashCode() != tableHash
                    System.out.println("Rover " + id + " next hop table: " + nextHop);
                    System.out.println();
                    printTable(); 
                    printHopTable();
                    tableCopy = new HashMap<>(table);
                }
            }

            // if (receivedTable.equals("EXIT")) socket.close();
        }
    }


    /**
     * This method is used to update self vector after receiving a RIPv2 packet.
     * 
     * @param receivedId -> id of the rover from which the vector was received.
     */
    private void updateSelf(int receivedId) {
        HashMap<Integer, Integer> self = table.getOrDefault(id, null);

        // received id is at hop 1
        if (self == null) {
            self = new HashMap<>();
            self.put(receivedId, 1);
        }

        // set of all possible destinations
        Set<Integer> destSet = new HashSet<Integer>();
        destSet.addAll(table.get(receivedId).keySet());
        destSet.addAll(self.keySet());
        destSet.add(receivedId);

        for (int destId: destSet) {
            if (destId == id) {
                self.put(id, 0);
                continue;
            }
            // if destination = received id, just directly compare.
            if (destId == receivedId && self.containsKey(receivedId) && table.containsKey(receivedId)) {
                if (self.get(receivedId) > table.get(receivedId).getOrDefault(id, 16)) {
                    self.put(receivedId, table.get(receivedId).get(id));
                    continue;
                }
            }


            int srcToDest = self.getOrDefault(destId, 16);
            // when destination is not direct link, direct dist = inf
            // if (!nextHop.containsKey(destId)) {
            //     srcToDest = 16;
            // } else if (nextHop.get(destId) != destId) {
            //     srcToDest = 16;
            // }

            int srcToInterm = self.getOrDefault(receivedId, 16);
            int IntermToDest = table.get(receivedId).getOrDefault(destId, 16);

            if (nextHop.containsKey(destId) && nextHop.get(destId) == receivedId && IntermToDest == 16) {
                nextHop.remove(destId);
            }

            if (nextHop.containsKey(destId) && nextHop.get(destId) != destId) {
                srcToDest = Math.max(srcToDest, srcToInterm + IntermToDest);
            }
            
            int clamped = Math.max(0, Math.min(srcToInterm + IntermToDest, 16));

            if (srcToDest > srcToInterm + IntermToDest) {
                self.put(destId, clamped);
                nextHop.put(destId, receivedId);
                // when dest not present in next hop table or dest is present and we are receiving updates from the next hop
                // put distance = sum of distances
            } else if (!nextHop.containsKey(destId) || nextHop.containsKey(destId) && nextHop.get(destId) == receivedId) {
                self.put(destId, clamped);
                nextHop.put(destId, receivedId);
            }
        }

        table.put(id, self);
        table.put(receivedId, receivedVectorCopy);
    }


    /**
     * This method helps decoding the RIPv2 packet. It takes as input a byte[]
     * and returns the vector of the sending rover.
     * 
     * @param bytes -> the RIPv2 packet.
     * @return HashMap -> vector of the sending rover.
     */
    public HashMap<Integer, HashMap<Integer, Integer>> getHashMap(byte[] bytes) {
        HashMap<Integer, HashMap<Integer, Integer>> result = new HashMap<>();

        int receivedId = -1;
        HashMap<Integer, Integer> receivedVector = new HashMap<>();
        receivedVectorCopy = new HashMap<>();

        int entries = (bytes.length - 4) / 20;
        for (int entry = 0; entry < entries; entry++) {
            int entryStart = entry * 20 + 4;
            int key = bytes[entryStart+6] & 0xff;
            int value = bytes[entryStart+19] & 0xff;
            // if (receivedId < 0) receivedId = bytes[entryStart + 14] & 0xff;
            if (value == 0) receivedId = key;
            receivedVector.put(key, value);
            receivedVectorCopy.put(key, value);

            int nextHop = bytes[entryStart + 14] & 0xff;

            // POISONED REVERSE
            if (nextHop == id && key != id) receivedVector.put(key, 16);
        }

        // SPLIT HORIZON
        
        // HashSet<Integer> toDelete = new HashSet<>();

        // for (int k: receivedVector.keySet()) {
        //     if (receivedVector.get(k) >= 16) {
        //         toDelete.add(k);
        //     }
        // }

        // for (int k: toDelete) {
        //     receivedVector.remove(k);
        //     // if (receivedVectorCopy.containsKey(k)) receivedVectorCopy.remove(k);
        // }

        result.put(receivedId, receivedVector);

        return result;
    }

    private void printTable() {
        Set<Integer> setOfKeys = new HashSet<>();
        setOfKeys.addAll(table.keySet());
        for (int key: table.keySet()) {
            setOfKeys.addAll(table.get(key).keySet());
        }
        int cols = Collections.max(setOfKeys)+1;
        int rows = Collections.max(table.keySet())+1;

        int[][] tableView = new int[rows][cols];

        for (int r: table.keySet()) {
            for (int c: setOfKeys) {
                tableView[r][0] = r;
                tableView[0][c] = c;
                tableView[r][c] = table.get(r).getOrDefault(c, 16);
            }
        }

        for (int i = 0; i < rows; i++) {
            if (i == 1) {
                for (int j = 0; j < 10*cols; j++) {
                    System.out.print("-");
                }
                System.out.println();
            }
            for (int j = 0; j < cols; j++) {
                if (i==0 && j==0) {
                    System.out.print(Router.getInternalIP(id) + "\t");
                    continue;
                }
                if (j==0 && tableView[i][j] == 0) {
                    break;
                }
                String val = (tableView[i][j] >= 16)? "inf": String.valueOf(tableView[i][j]);
                if (j==0) System.out.print("\t" + val + "\t");
                else System.out.print(val + "\t");
            }
            System.out.println();
        }
        System.out.println();
    }


    private void printHopTable() {
        System.out.println("Dest\t\tNextHop\t\tCost");
        System.out.println("===============================================");
        // Added this line without testing
        System.out.println(Router.getInternalIP(id) + "\t" + externalIP.get(id) + 
                    "\t" + 0);
        for (int key: nextHop.keySet()) {
            int cost = table.get(id).get(key);
            if (cost < 16) {
                System.out.println(Router.getInternalIP(key) + "\t" + externalIP.get(nextHop.get(key)) + 
                    "\t" + cost);
            }
        }
        System.out.println();
    }
}