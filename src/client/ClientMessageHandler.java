package client;

import core.Message;

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
    protected String username;
    protected final ClientMessageListener listener;
    protected DatagramSocket socket;
    protected String token;
    protected final ClientUI ui;

    protected final Lock lock = new ReentrantLock();
    protected final Condition notEmpty = lock.newCondition();

    protected static final String LOGIN_REGEX = "server->(\\S+)#(?:Success\\<(.*)\\>|Error: password does not match!)";
    protected static final Pattern LOGIN_PATTERN = Pattern.compile(LOGIN_REGEX);

    public ClientMessageHandler(ClientUI ui) throws IOException {

        this.ui = ui;

        listener = new ClientMessageListener(ui);
        listener.start();

        ui.getSendQueue().addActionListener(actionEvent -> {
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

    public void attemptConnect(String password, int port) throws IOException {
        byte[] buf = (username+"->server#login<"+password+">").getBytes();
        if (socket!=null){
            socket.close();
        }
        setSocket(new DatagramSocket(port,ui.fetchServerIP()));
        // send the request
        socket.send(new DatagramPacket(buf, buf.length, ui.fetchServerIP(), ui.fetchServerPort()));
        String ret = listener.waitFor(LOGIN_REGEX, 2000);
        if (ret==null){
            // most likely the server is offline
            return;
        }
        Matcher loginMatcher = LOGIN_PATTERN.matcher(ret);
        if (loginMatcher.matches()&&loginMatcher.group(1).equals(username)){
            if (loginMatcher.group(2)==null){
                // login failed
                ui.deliverSystemMessage(ret);
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
            while (!ui.getSendQueue().isEmpty()) {
                if (socket==null){
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                Message m = ui.getSendQueue().remove();
                try {
                    if (m.getContents().equals("/logoff")&&m.getDest().equals("server")){
                        sendLogoff();
                    }else {
                        if (!m.getContents().equals("Received")) {
                            sendMessage(m);
                        }else{
                            // Do not wait for confirmation when sending a confirmation
                            sendMessage(m,false);
                        }

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

    public boolean logoff() throws IOException {
        sendLogoff();
        String sanitizedToken = token.replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        String confirm = listener.waitFor("server-\\>"+username+"#Success\\<"+sanitizedToken+"\\>", 2000);
        if (confirm!=null){
            token = null;
            socket.close();
            setSocket(null);
        }
        return confirm != null;
    }

    protected void sendLogoff() throws IOException {
        byte[] replyBuf = (username+"->server#logoff<"+token+">").getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,ui.fetchServerIP(),ui.fetchServerPort());
        socket.send(retPacket);
    }

    protected void sendMessage(Message m) throws IOException {
        sendMessage(m, true);
    }

    protected void sendMessage(Message m, boolean confirmReceived) throws IOException {
        byte[] replyBuf = (m.getSource()+"->"+m.getDest()+"#<"+token+"><"+m.getId()+">"+m.getContents()).getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,ui.fetchServerIP(),ui.fetchServerPort());
        socket.send(retPacket);
        // replace special characters with same character with preceding backslash
        String sanitizedToken = token.replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        String sanitizedContents = m.getContents().replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        if (confirmReceived) {
            String confirm = listener.waitFor("server-\\>" + m.getSource() + "#\\<" + sanitizedToken + "\\>\\<" + m.getId() + "\\>Success: " + sanitizedContents, 2000);
            if (confirm == null) {
                // reattempt
                sendMessage(m);
            }
        }
    }


    public void setUserID(String userID) {
        this.username = userID;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
        listener.socket = socket;
    }
}
