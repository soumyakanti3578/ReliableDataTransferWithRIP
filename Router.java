/**
 * This class represents a router, which is inside the Rover.
 * 
 * @author Soumyakanti Das
 */

import java.util.HashMap;
import java.net.InetAddress;


public class Router {

    private int id;
    private InetAddress group;

    // maps rover id (from) -> HashMap of id(to) -> hops(cost). 
    // This is a sparse representation of the matrix.
    private HashMap<Integer, HashMap<Integer, Integer>> table;
    // stores latest update time of a rover
    private HashMap<Integer, Long> ttlMap;

    // A router has a Receiver, Sender, and TTL Checker.
    Sender sender;
    Receiver receiver;
    TTLChecker checker;

    public Router(int port, int id, String group, HashMap<Integer,
     Integer> nextHop, HashMap<Integer, String> externalIP) {
        this.id = id;
        try {
            this.group = InetAddress.getByName(group);
        } catch (Exception e) {
            e.printStackTrace();
        }
        table = new HashMap<>();
        ttlMap = new HashMap<>();
        // nextHop = new HashMap<>();
        // externalIP = new HashMap<>();

        sender = new Sender(id, port, this.group, table, nextHop);
        receiver = new Receiver(id, port, this.group, table, nextHop, ttlMap, externalIP);
        checker = new TTLChecker(id, table, nextHop, ttlMap);
    }

    public void start() {
        System.out.println("Rover " + id + " booting up...");
        receiver.start();
        checker.start();
        sender.start();
    }

    public static String getInternalIP(int id) {
        return "10.0." + id + ".0/24";
    }

    public static int getRoverIdFromInternalIP(String internalIP) {
        return Integer.parseInt(internalIP.split("\\.")[2]);
    }
}