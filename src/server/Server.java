package server;

import core.Message;
import javafx.util.Pair;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

// TODO encryption
// TODO message delivery verification
public class Server extends Thread {
    protected static final String CLIENT_MSG_REGEX = "(\\S+)-\\>(\\S+)#(.*)";
    protected static final Pattern CLIENT_MSG_PATTERN = Pattern.compile(CLIENT_MSG_REGEX);
    protected static final String CLIENT_LOGIN_REGEX = "login\\<(.*)\\>";
    protected static final Pattern CLIENT_LOGIN_PATTERN = Pattern.compile(CLIENT_LOGIN_REGEX);
    protected static final String CLIENT_LOGOFF_REGEX = "logoff\\<(.*)\\>";
    protected static final Pattern CLIENT_LOGOFF_PATTERN = Pattern.compile(CLIENT_LOGOFF_REGEX);
    protected static final String DIRECT_MSG_REGEX = "\\<(.{6})\\>\\<(\\d{10})\\>(.*)";
    protected static final Pattern DIRECT_MSG_PATTERN = Pattern.compile(DIRECT_MSG_REGEX);
    protected static final String KEY_REGEX = "\\S+->server#key:(.*)";
    protected static final Pattern KEY_PATTERN = Pattern.compile(KEY_REGEX);
    protected final File passwordsFile = new File("passwords");
    protected HashMap<String, String> clientPasswords;
    protected Hashtable<String, ClientData> clientDataMap;
    protected DatagramSocket socket;
    protected SecureRandom rand = new SecureRandom();
    /**
     * map of message id vs the thread responsible for delivery guarantee
     */
    protected Hashtable<String, ResenderThread> expectedReplies = new Hashtable<>();

    protected final KeyPair keyPair;
    protected Cipher decryptCipher;

    ArrayList<Pair<DatagramPacket, byte[]>> buffer = new ArrayList<>();

    public static void main(String[] args) {
        Server server = new Server(12224);
        server.start();
    }

    public Server(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        if (passwordsFile.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(passwordsFile))) {
                clientPasswords = (HashMap<String, String>) in.readObject();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            clientPasswords = new HashMap<>();
        }
        clientDataMap = new Hashtable<>();
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
    }

    public void run() {
        byte[] buf = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet;
                if (buffer.isEmpty()) {
                    packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                } else {
                    Pair data = buffer.remove(0);
                    buf = (byte[]) data.getValue();
                    packet = (DatagramPacket) data.getKey();
                }

                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                String clientMsg = new String(packet.getData(), 0, packet.getLength());

                System.out.println("Server received raw message:\n\t" + clientMsg);

                if (!checkFormatting(clientMsg)) {
                    sendMessageUnencrypted(clientAddr, clientPort, "Unrecognized message format");
                    continue;
                }

                Matcher msgMatcher = CLIENT_MSG_PATTERN.matcher(clientMsg);
                msgMatcher.find();
                String source = msgMatcher.group(1);
                String dest = msgMatcher.group(2);
                String contents = msgMatcher.group(3);

                if (contents.equals("reqkey")) {
                    sendMessageUnencrypted(clientAddr, clientPort, "server->" + source + "#key:" + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
                    continue;
                }

                // decrypt the message
                byte[] encryptedContents = Base64.getDecoder().decode(contents);
                byte[] decryptedContents = decryptCipher.doFinal(encryptedContents);
                contents = new String(decryptedContents);
                clientMsg = source + "->" + dest + "#" + contents;

                System.out.println("Server received decrypted message:\n\t" + clientMsg);

                // check if this was an expected message
                // uses the general Message class because that does the parsing easily
                Message m = Message.fromRaw(clientMsg);
                if (m != null && m.getDest().equals("server") && m.getContents().equals("Received")) {
                    if (expectedReplies.containsKey(m.getId())) {
                        expectedReplies.remove(m.getId()).finish();
                    }
                    continue;
                }
                Matcher loginMatcher = CLIENT_LOGIN_PATTERN.matcher(contents);
                if (dest.equals("server") && loginMatcher.matches()) {
                    // login
                    ClientData data = login(source, clientAddr, clientPort, clientMsg);
                    reqKey(data);
                    if (data.isLoggedIn()) {
                        // reply success
                        sendMessage(data, "server->" + source + "#Success<" + data.getToken() + ">");
                    } else {
                        // login failed
                        sendMessage(data, "server->" + source + "#Error: password does not match!");
                    }
                } else {
                    if (!clientDataMap.containsKey(source)) {
                        sendMessageUnencrypted(clientAddr, clientPort, "server->" + source + "#Error: Missing key");
                        continue;
                    }
                    ClientData clientData = clientDataMap.get(source);
                    if (clientData.getKey()==null){
                        reqKey(clientData);
                    }
                    if (dest.equals("server") && CLIENT_LOGOFF_PATTERN.matcher(contents).matches()) {
                        // logoff
                        Matcher matcher = CLIENT_LOGOFF_PATTERN.matcher(contents);
                        matcher.find();
                        String token = matcher.group(1);
                        if (clientData.getToken().equals(token)) {
                            // success
                            sendMessage(clientData, "server->" + source + "#Success<" + token + ">");
                            clientData.setLoggedIn(false);
                        }
                    } else {
                        // just a normal message
                        Matcher contentMatcher = DIRECT_MSG_PATTERN.matcher(contents);
                        if (!contentMatcher.matches()) {
                            // skip this message (probably a duplicate confirmation?)
                            continue;
                        }
                        String token = contentMatcher.group(1);
                        String msgID = contentMatcher.group(2);
                        String msgContent = contentMatcher.group(3);
                        if (clientData.isLoggedIn() && clientData.getToken().equals(token)) {
                            clientData.updateLastSeen();
                            startExpirationTimer(clientData);
                            if (clientDataMap.containsKey(dest) && clientDataMap.get(dest).isLoggedIn()) {
                                ClientData destData = clientDataMap.get(dest);
                                String msgText = source + "->" + dest + "#<" + destData.getToken() + "><" + msgID + ">" + msgContent;
                                sendMessage(destData, msgText);
                                expectedReplies.put(msgID, new ResenderThread(destData, msgText));
                                sendMessage(clientData, "server->" + source + "#<" + token + "><" + msgID + ">Success: " + msgContent);
                            } else {
                                sendMessage(clientData, "server->" + source + "#<" + token + "><" + msgID + ">Error: destination offline");
                            }
                        } else {
                            sendMessage(clientData, "server->" + source + "#<" + token + "><" + msgID + ">Error: token error!");
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
            }
        }
    }

    private void reqKey(ClientData data) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        sendMessageUnencrypted(data.addr, data.port, "server->" + data.getName() + "#reqkey");
        String keyMsg = "";
        while (!KEY_PATTERN.matcher(keyMsg).matches()) {
            socket.receive(packet);
            keyMsg = new String(buf, 0, packet.getLength());
            if (!KEY_PATTERN.matcher(keyMsg).matches()) {
                buffer.add(new Pair<>(packet, Arrays.copyOf(buf, buf.length)));
            }
        }
        Matcher keyMatcher = KEY_PATTERN.matcher(keyMsg);
        keyMatcher.find();
        String key = keyMatcher.group(1);
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PublicKey clientKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        data.setKey(clientKey);
    }

    protected void sendMessage(ClientData clientData, String msg) throws IOException {
        System.out.println("Server encrypting raw message:\n\t" + msg);

        byte[] replyBuf = msg.getBytes();
        try {
            replyBuf = encrypt(msg, clientData.getKey()).getBytes();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        System.out.println("Server sending message:\n\t" + new String(replyBuf));
        DatagramPacket retPacket = new DatagramPacket(replyBuf, replyBuf.length, clientData.getAddr(), clientData.getPort());
        socket.send(retPacket);
    }

    protected void sendMessageUnencrypted(InetAddress addr, int port, String msg) throws IOException {
        System.out.println("Server sending message:\n\t" + msg);
        byte[] replyBuf = msg.getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf, replyBuf.length, addr, port);
        socket.send(retPacket);
    }

    protected String encrypt(String msg, PublicKey key) throws BadPaddingException, IllegalBlockSizeException {

        Matcher msgMatcher = CLIENT_MSG_PATTERN.matcher(msg);
        msgMatcher.find();
        String source = msgMatcher.group(1);
        String dest = msgMatcher.group(2);
        String contents = msgMatcher.group(3);

        Cipher encryptCipher = null;
        try {
            encryptCipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        try {
            encryptCipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        String encodedContents = new String(Base64.getEncoder().encode(encryptCipher.doFinal(contents.getBytes())));
        return source+"->"+dest+"#"+encodedContents;
    }

    protected boolean checkFormatting(String clientMsg) {
        return CLIENT_MSG_PATTERN.matcher(clientMsg).matches();
    }

    /**
     * Logs a client in, given the client information and assuming that the password either matches or does not exist.
     *
     * @param name
     * @param addr
     * @param port
     * @param contents
     */
    protected ClientData login(String name, InetAddress addr, int port, String contents) {
        Matcher loginMatch = CLIENT_LOGIN_PATTERN.matcher(contents);
        loginMatch.find();
        String password = loginMatch.group(1);
        String token = generateToken();
        ClientData clientData = new ClientData(name, addr, port, token);
        clientDataMap.put(name, clientData);
        if (clientPasswords.containsKey(name)) {
            if (clientPasswords.get(name).equals(password)) {
                // password exists and is validated
                startExpirationTimer(clientData);
                clientData.setLoggedIn(true);
            }
        } else {
            // first time we see this login
            clientPasswords.put(name, password);
            startExpirationTimer(clientData);
            clientData.setLoggedIn(true);
            savePasswords();
        }
        return clientData;
    }

    /**
     * Save the password file, but this operation is NOT threadsafe so if this is actually running multithreaded it
     * needs to be replaced.
     * <p>
     * TODO make saving the passwords threadsafe
     */
    protected void savePasswords() {
        File temp = new File("passwords.tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(temp))) {
            out.writeObject(clientPasswords);
            // attempt to do an atomic move so the file is not lost if something happens during the write.
            Files.move(temp.toPath(), passwordsFile.toPath(), ATOMIC_MOVE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate a random 6 character token
     *
     * @return the token
     */
    protected String generateToken() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            // characters from 31-126, non-whitespace ascii characters
            builder.append((char) (rand.nextInt(96) + 31));
        }
        String token = builder.toString();
        if (token.contains("#")){
            // we dont want to generate tokens containing '#'
            return generateToken();
        }else {
            return token;
        }
    }

    protected void startExpirationTimer(ClientData client) {
        Timer timer = new Timer("Timeout");
        timer.schedule(new TimeoutTask(client), 301000);
    }

    private class TimeoutTask extends TimerTask {
        private final ClientData client;

        TimeoutTask(ClientData client) {
            this.client = client;
        }

        @Override
        public void run() {
            // hasn't been seen in 5 minutes
            if (System.currentTimeMillis() - client.getLastSeen() > 300000) {
                client.setLoggedIn(false);
            }
        }
    }

    private class ResenderThread extends Thread {
        private final ClientData clientData;
        private final String msg;

        private boolean finished = false;

        private ResenderThread(ClientData clientData, String msg) {
            this.clientData = clientData;

            this.msg = msg;
        }

        public void finish() {
            finished = true;
        }

        public void run() {
            while (!finished) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!finished) {
                    try {
                        sendMessage(clientData, msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
