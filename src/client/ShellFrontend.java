package client;

import core.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Scanner;

public class ShellFrontend extends Thread implements ClientUI {
    Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
    String userID;
    String password;
    int localPort;
    MessageQueue sendQueue;
    MessageQueue receiveQueue;
    Random rand = new Random();

    public static void main (String args[]){
        ShellFrontend frontend = new ShellFrontend();
        frontend.start();
    }

    public ShellFrontend(){
        System.out.println("Local Port: ");
        localPort = scanner.nextInt();
        // ignore first break
        scanner.nextLine();
        System.out.println("Username: ");
        userID = scanner.nextLine();
        System.out.println("Password: ");
        password = scanner.nextLine();
        sendQueue = new MessageQueue();
        receiveQueue = new MessageQueue();
        Thread receiveThread = new Thread(new Runnable(){

            @Override
            public void run() {
                while(true) {
                    if (!receiveQueue.isEmpty()) {
                        System.out.println(receiveQueue.remove());
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        receiveThread.start();
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
            String dest = scanner.nextLine();
            System.out.println("Message: ");
            String contents = scanner.nextLine();
            Message m = new Message(userID, dest,rand,contents);
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
    public MessageQueue getReceiveQueue() {
        return receiveQueue;
    }

    @Override
    public MessageQueue getSendQueue() {
        return sendQueue;
    }
}
