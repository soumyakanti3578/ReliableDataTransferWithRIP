import java.util.HashMap;

/**
 * This is the entrypoint. run.sh should start this class.
 * 
 * @author Soumyakanti Das
 */

public class Rover {

    private static int port = 63000;
    private static String group = "230.230.230.230";
    public static HashMap<Integer, Integer> nextHop = new HashMap<>();
    // maps rover id to external IP
    public static HashMap<Integer, String> externalIP = new HashMap<>();
    private static Manager manager;
    public static int selfID;

    public static void main(String[] args) {

        // if (args.length != 1 && args.length != 3) {
        //     System.out.println("Usage: java Rover id [destination_ip filename]");
        //     System.exit(-1);
        // }

        selfID = Integer.parseInt(args[0]);

        Router r = new Router(port, selfID, group, nextHop, externalIP);
        r.start();
        manager = new Manager();
        manager.start();

        if (args.length == 3) {
            String destIP = args[1];
            String fileName = args[2];
            int destID = Router.getRoverIdFromInternalIP(destIP);

            System.out.println("destID: " + destID);

            while (nextHop == null || !nextHop.containsKey(destID)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // wait for network to settle
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            manager.sendFile(fileName, destID);
        }
    }
}
