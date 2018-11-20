package server;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

// TODO implement server token timeouts
// TODO encryption
// TODO message delivery verification
public class Server extends Thread{
    protected static final String CLIENT_MSG_REGEX = "(\\S+)-\\>(\\S+)#(.*)";
    protected static final Pattern CLIENT_MSG_PATTERN = Pattern.compile(CLIENT_MSG_REGEX);
    protected static final String CLIENT_LOGIN_REGEX = "login\\<(.*)\\>";
    protected static final Pattern CLIENT_LOGIN_PATTERN = Pattern.compile(CLIENT_LOGIN_REGEX);
    protected static final String DIRECT_MSG_REGEX = "\\<(\\S{6})\\>\\<(\\d{10})\\>(.*)";
    protected static final Pattern DIRECT_MSG_PATTERN = Pattern.compile(DIRECT_MSG_REGEX);
    protected final File passwordsFile = new File("passwords");
    protected HashMap<String, String> clientPasswords;
    protected HashMap<String,ClientData> clientDataMap;
    protected DatagramSocket socket;
    protected SecureRandom rand = new SecureRandom();

    public static void main(String[] args){
        Server server = new Server(12224);
        server.start();
    }

    public Server(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        if (passwordsFile.exists()){
            try(ObjectInputStream in = new ObjectInputStream(new FileInputStream(passwordsFile))){
                clientPasswords = (HashMap<String, String>)in.readObject();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }else{
            clientPasswords = new HashMap<>();
        }
        clientDataMap = new HashMap<>();
    }

    public void run(){
        byte[] buf = new byte[1024];
        while (true){
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String clientMsg = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Server received raw message:\n"+clientMsg);
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                if (!checkFormatting(clientMsg)){
                    sendMessage(clientAddr,clientPort,"Unrecognized message format");
                    continue;
                }

                Matcher msgMatcher = CLIENT_MSG_PATTERN.matcher(clientMsg);
                msgMatcher.find();
                String source = msgMatcher.group(1);
                String dest = msgMatcher.group(2);
                String contents = msgMatcher.group(3);

                if (dest.equals("server")&&CLIENT_LOGIN_PATTERN.matcher(contents).matches()){
                    ClientData data = login(source,clientAddr,clientPort,clientMsg);
                    if (data!=null){
                        // reply success
                        sendMessage(clientAddr,clientPort,"server->"+source+"#Success<"+data.getToken()+">");
                    }
                }else{
                    // just a normal message
                    Matcher contentMatcher = DIRECT_MSG_PATTERN.matcher(contents);
                    contentMatcher.find();
                    String token = contentMatcher.group(1);
                    String msgID = contentMatcher.group(2);
                    String msgContent = contentMatcher.group(3);
                    if (clientDataMap.containsKey(source)&&clientDataMap.get(source).getToken().equals(token)) {
                        if (clientDataMap.containsKey(dest)) {
                            ClientData destData = clientDataMap.get(dest);
                            sendMessage(destData.getAddr(), destData.getPort(), source + "->" + dest + "#<" + destData.getToken() + "><" + msgID + ">" + msgContent);
                            sendMessage(clientAddr, clientPort, "server->" + source + "#<" + token + "><" + msgID + ">Success: " + msgContent);
                        }else{
                            sendMessage(clientAddr, clientPort, "server->" + source + "#<" + token + "><" + msgID + ">Error: destination offline");
                        }
                    }else{
                        sendMessage(clientAddr,clientPort,"server->"+source+"#<"+token+"><"+msgID+">Error: token error!");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void sendMessage(InetAddress clientAddr, int clientPort, String msg) throws IOException {
        System.out.println("Server sending message:\n"+msg);
        byte[] replyBuf = msg.getBytes();
        DatagramPacket retPacket = new DatagramPacket(replyBuf,replyBuf.length,clientAddr,clientPort);
        socket.send(retPacket);
    }

    protected boolean checkFormatting(String clientMsg){
        return CLIENT_MSG_PATTERN.matcher(clientMsg).matches();
    }
    /**
     * Logs a client in, given the client information and assuming that the password either matches or does not exist.
     * @param name
     * @param addr
     * @param port
     * @param contents
     */
    protected ClientData login(String name, InetAddress addr, int port, String contents){
        Matcher loginMatch = CLIENT_LOGIN_PATTERN.matcher(contents);
        loginMatch.find();
        String password = loginMatch.group(1);
        if (clientPasswords.containsKey(name)){
            if (clientPasswords.get(name).equals(password)){
                // password exists and is validated
                String token = generateToken();
                ClientData clientData = new ClientData(name, addr, port, token);
                clientDataMap.put(name,clientData);
                return clientData;
            }else{
                return null;
            }
        }else{
            // first time we see this login
            clientPasswords.put(name, password);
            String token = generateToken();
            ClientData clientData = new ClientData(name, addr, port, token);
            clientDataMap.put(name,clientData);
            savePasswords();
            return clientData;
        }
    }

    /**
     * Save the password file, but this operation is NOT threadsafe so if this is actually running multithreaded
     * it needs to be replaced.
     *
     * TODO make saving the passwords threadsafe
     */
    protected void savePasswords(){
        File temp = new File("passwords.tmp");
        try(ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(temp))){
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
     * @return the token
     */
    protected String generateToken(){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++){
            // characters from 21-126, non-whitespace ascii characters
            builder.append((char)(rand.nextInt(106)+21));
        }
        return builder.toString();
    }

}
