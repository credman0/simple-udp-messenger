package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientMessageHandler extends Thread {
    protected final String username;
    protected final MessageQueue queue;
    protected final DatagramSocket socket;
    protected final InetAddress serverAddr;
    protected final int serverPort;
    protected String token;

    protected final Lock lock = new ReentrantLock();
    protected final Condition notEmpty = lock.newCondition();

    protected static final String LOGIN_REGEX = "server->(\\S+)#Success\\<(.*)\\>";
    protected static final Pattern LOGIN_PATTERN = Pattern.compile(LOGIN_REGEX);

    public ClientMessageHandler(String username, MessageQueue queue, DatagramSocket socket, InetAddress serverAddr, int serverPort) throws IOException {
        this.username = username;
        this.queue = queue;
        this.socket = socket;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;

        queue.addActionListener(actionEvent -> {
            if (actionEvent.getActionCommand().equals("add")){
                lock.lock();
                try {
                    notEmpty.signalAll();
                }finally {
                    lock.unlock();
                }
            }
        });

    }

    public void attemptConnect(String password) throws IOException {
        socket.setSoTimeout(2000);
        byte[] buf = (username+"->server#login<"+password+">").getBytes();
        byte[] replyBuf = new byte[1024];
        boolean success = false;
        int attempts = 0;
        while (!success && attempts<5) {
            // send the request
            socket.send(new DatagramPacket(buf, buf.length, serverAddr, serverPort));
            DatagramPacket retPacket = new DatagramPacket(replyBuf, replyBuf.length);
            // expect response
            socket.receive(retPacket);
            System.out.println(new String(retPacket.getData(),0, retPacket.getLength()));
            Matcher loginMatcher = LOGIN_PATTERN.matcher(new String(retPacket.getData(),0,retPacket.getLength()));
            if (loginMatcher.matches()&&loginMatcher.group(1).equals(username)){
                success = true;
                token = loginMatcher.group(2);
            }else{
                attempts++;
            }
        }
    }

    public boolean isConnected(){
        return token!=null;
    }

    public void run(){
        while (true) {
            while (!queue.isEmpty()) {
                Message m = queue.remove();
                try {
                    sendMessage(m);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            lock.lock();
            try {
                notEmpty.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    protected void sendMessage(Message m) throws IOException {
        byte[] replyBuf = (username+"->"+m.getDest()+"#<"+token+"><"+m.getId()+">"+m.getContents()).getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,serverAddr,serverPort);
        socket.send(retPacket);
    }
}
