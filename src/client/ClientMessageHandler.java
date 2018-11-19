package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientMessageHandler extends Thread {
    protected final String username;
    protected final String password;
    protected final MessageQueue queue;
    protected final DatagramSocket socket;
    protected String token;

    protected static final String LOGIN_REGEX = "server->(\\S+)#Success\\<(.*)\\>";
    protected static final Pattern LOGIN_PATTERN = Pattern.compile(LOGIN_REGEX);

    public ClientMessageHandler(String username, String password, MessageQueue queue, DatagramSocket socket) throws IOException {
        this.username = username;
        this.password = password;
        this.queue = queue;
        this.socket = socket;

        queue.addActionListener(actionEvent -> {
            if (actionEvent.getActionCommand().equals("add")){
                notifyAll();
            }
        });
        socket.setSoTimeout(2000);
        byte[] buf = (username+"->server#login<"+password+">").getBytes();
        byte[] replyBuf = new byte[1024];
        boolean success = false;
        int attempts = 0;
        while (!success && attempts<5) {
            socket.send(new DatagramPacket(buf, buf.length));
            DatagramPacket retPacket = new DatagramPacket(replyBuf, replyBuf.length, socket.getInetAddress(), socket.getPort());
            socket.receive(retPacket);
            Matcher loginMatcher = LOGIN_PATTERN.matcher(new String(retPacket.getData(),0,retPacket.getLength()));
            if (loginMatcher.matches()&&loginMatcher.group(1).equals("username")){
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
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected void sendMessage(Message m) throws IOException {
        byte[] replyBuf = (username+"->"+m.getDest()+"#<"+token+"><"+m.getId()+">"+m.getContents()).getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length);
        socket.send(retPacket);
    }
}
