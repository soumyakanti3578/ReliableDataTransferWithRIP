/**
 * This class is used to send data through UDP
 * 
 * @author Soumyakanti Das
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class DataSender {

    public void send(byte[] data, int dest, int port) {
        InetAddress destination = null;
        try {
            destination = InetAddress.getByName(Rover.externalIP.get(Rover.nextHop.get(dest)));
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }

        while (Rover.externalIP.get(Rover.nextHop.get(dest)) == null) {
            try{
                System.out.println("Waiting for nextHop table to get fixed");
                Thread.sleep(1000);
                destination = InetAddress.getByName(Rover.externalIP.get(Rover.nextHop.get(dest)));
            } catch (UnknownHostException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        try(DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(data, data.length, destination, port);
            socket.send(packet);
            Manager.lastSentTime = System.currentTimeMillis();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}