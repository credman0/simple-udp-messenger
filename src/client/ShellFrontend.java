package client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Scanner;

public class ShellFrontend extends Thread implements ClientUI {
    Scanner scanner = new Scanner(System.in);
    String userID;
    String password;
    int localPort;
    MessageQueue sendQueue;
    Random rand = new Random();

    public static void main (String args[]){
        ShellFrontend frontend = new ShellFrontend();
        frontend.start();
    }

    public ShellFrontend(){
        System.out.println("Local Port: ");
        localPort = scanner.nextInt();
        System.out.println("Username: ");
        userID = scanner.next();
        System.out.println("Password: ");
        password = scanner.next();
        sendQueue = new MessageQueue();
        ClientBackend backend = null;
        try {
            backend = new ClientBackend(new DatagramSocket(localPort, fetchServerIP()), this);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        backend.start();
    }

    public void run(){
        while (true){
            System.out.println("Destination: ");
            String dest = scanner.next();
            System.out.println("Message: ");
            String contents = scanner.next();
            Message m = new Message(dest,rand,contents);
            sendQueue.add(m);
        }
    }

    @Override
    public InetAddress fetchServerIP() {
        try {
            return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int fetchServerPort() {
        return 12224;
    }

    @Override
    public String fetchUserID() {
        return userID;
    }

    @Override
    public String fetchPassword() {
        return password;
    }

    @Override
    public MessageQueue getSendQueue() {
        return sendQueue;
    }
}
