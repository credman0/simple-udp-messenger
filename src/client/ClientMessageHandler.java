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
    protected final ClientMessageListener listener;
    protected final MessageQueue sendQueue;
    protected final DatagramSocket socket;
    protected final InetAddress serverAddr;
    protected final int serverPort;
    protected String token;

    protected final Lock lock = new ReentrantLock();
    protected final Condition notEmpty = lock.newCondition();

    protected static final String LOGIN_REGEX = "server->(\\S+)#(?:Success\\<(.*)\\>|Error: password does not match!)";
    protected static final Pattern LOGIN_PATTERN = Pattern.compile(LOGIN_REGEX);

    public ClientMessageHandler(String username, MessageQueue receiveQueue, MessageQueue sendQueue, DatagramSocket socket, InetAddress serverAddr, int serverPort) throws IOException {
        this.username = username;
        this.sendQueue = sendQueue;
        this.socket = socket;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;

        listener = new ClientMessageListener(receiveQueue, socket);
        listener.start();

        sendQueue.addActionListener(actionEvent -> {
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
        byte[] buf = (username+"->server#login<"+password+">").getBytes();
        // send the request
        socket.send(new DatagramPacket(buf, buf.length, serverAddr, serverPort));
        String ret = listener.waitFor(LOGIN_REGEX, 2000);
        Matcher loginMatcher = LOGIN_PATTERN.matcher(ret);
        if (loginMatcher.matches()&&loginMatcher.group(1).equals(username)){
            if (loginMatcher.group(2)==null){
                // login failed
                System.out.println("Received response "+ ret);
            }else {
                token = loginMatcher.group(2);
            }
        }

    }

    public boolean isConnected(){
        return token!=null;
    }

    public void run(){
        while (true) {
            while (!sendQueue.isEmpty()) {
                Message m = sendQueue.remove();
                try {
                    if (m.getContents().equals("/logoff")&&m.getDest().equals("server")){
                        sendLogoff();
                    }else {
                        sendMessage(m);
                    }
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

    protected void sendLogoff() throws IOException {
        byte[] replyBuf = (username+"->server#logoff<"+token+">").getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,serverAddr,serverPort);
        socket.send(retPacket);
    }

    protected void sendMessage(Message m) throws IOException {
        byte[] replyBuf = (m.getSource()+"->"+m.getDest()+"#<"+token+"><"+m.getId()+">"+m.getContents()).getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,serverAddr,serverPort);
        socket.send(retPacket);
        // replace special characters with same character with preceding backslash
        String sanitizedToken = token.replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        String confirm = listener.waitFor("server-\\>"+m.getSource()+"#\\<"+sanitizedToken+"\\>\\<"+m.getId()+"\\>Success: "+m.getContents(), 2000);
        System.out.println("confirmed "+confirm);
    }


}
