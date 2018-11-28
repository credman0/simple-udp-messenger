package client;

import core.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
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

    protected static final String KEY_REGEX = "server->\\S+#key:(.*)";
    protected static final Pattern KEY_PATTERN = Pattern.compile(KEY_REGEX);
    
    protected final KeyPair keyPair;
    protected PublicKey serverKey = null;
    protected Cipher encryptCipher;
    protected Cipher decryptCipher;

    public ClientMessageHandler(ClientUI ui) throws IOException {

        this.ui = ui;

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
        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
        try {
            decryptCipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        try {
            decryptCipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        listener = new ClientMessageListener(ui, decryptCipher);
        listener.start();
    }

    public void attemptConnect(String password, int port) throws IOException {
        if (socket!=null){
            socket.close();
        }
        setSocket(new DatagramSocket(port,ui.fetchServerIP()));

        byte[] buf = (ui.fetchUserID()+"->server#reqkey").getBytes();
        sendRawUnencrypted(buf);
        String ret = listener.waitFor(KEY_REGEX, 2000, false);
        if (ret==null){
            // most likely the server is offline
            return;
        }
        Matcher keyMatcher = KEY_PATTERN.matcher(ret);
        if (keyMatcher.matches()){
            String key = keyMatcher.group(1);
            byte[] keyBytes = Base64.getDecoder().decode(key);
            try {
                serverKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            try {
                encryptCipher = Cipher.getInstance("RSA");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }
            try {
                encryptCipher.init(Cipher.ENCRYPT_MODE, serverKey);
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        // send the request
        sendRaw(username, "server", "login<"+password+">"+new String(Base64.getEncoder().encode(keyPair.getPublic().getEncoded())));
        ret = listener.waitFor(LOGIN_REGEX, 2000, true);
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
        String confirm = listener.waitFor("server-\\>"+username+"#Success\\<"+sanitizedToken+"\\>", 2000, true);
        if (confirm!=null){
            token = null;
            socket.close();
            setSocket(null);
        }
        return confirm != null;
    }

    protected void sendLogoff() throws IOException {
        sendRaw(username,"server", "logoff<"+token+">");
    }

    protected void sendMessage(Message m) throws IOException {
        sendMessage(m, true);
    }

    protected void sendMessage(Message m, boolean confirmReceived) throws IOException {
        sendRaw(m.getSource(),m.getDest(),m.getContents());
        // replace special characters with same character with preceding backslash
        String sanitizedToken = token.replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        String sanitizedContents = m.getContents().replaceAll("[-.\\+*?\\[^\\]$(){}=!<>|:\\\\]", "\\\\$0");
        if (confirmReceived) {
            String confirm = listener.waitFor("server-\\>" + m.getSource() + "#\\<" + sanitizedToken + "\\>\\<" + m.getId() + "\\>Success: " + sanitizedContents, 2000, true);
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

    protected void sendRawUnencrypted(byte[] buf) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf,buf.length,ui.fetchServerIP(),ui.fetchServerPort());
        socket.send(packet);
    }

    protected void sendRaw(String source, String dest, String contents) throws IOException {
        String encodedContents = null;
        try {
            encodedContents  = encrypt(contents);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        byte[] buf = (source+"->"+dest+"#"+encodedContents).getBytes();
        DatagramPacket packet = new DatagramPacket(buf,buf.length,ui.fetchServerIP(),ui.fetchServerPort());
        socket.send(packet);
    }

    protected String encrypt(String contents) throws BadPaddingException, IllegalBlockSizeException {
        return new String(Base64.getEncoder().encode(encryptCipher.doFinal(contents.getBytes())));
    }
}
