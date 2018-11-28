package client;

import core.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Base64;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientMessageListener extends Thread{
    protected DatagramSocket socket;
    protected final Hashtable<Pattern, Condition> expectedTable = new Hashtable<>();
    protected final Hashtable<Pattern, String> expectedTableOut = new Hashtable<>();
    protected final Hashtable<Pattern, Boolean> expectEncrypted = new Hashtable<>();
    protected final Lock lock = new ReentrantLock();
    protected final ClientUI ui;
    protected final Cipher decryptCipher;

    protected final static String GENERAL_MSG_REGEX = "(\\S+)->(\\S+)#(.*)";
    protected final static Pattern GENERAL_MSG_PATTERN = Pattern.compile(GENERAL_MSG_REGEX);

    public ClientMessageListener(ClientUI ui, Cipher decryptCipher){
        this.ui = ui;
        this.decryptCipher = decryptCipher;
    }

    public void run(){
        byte[] buf = new byte[1024];
        while (true) {
            if (socket==null || socket.isClosed()){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String rawMsg = new String(packet.getData(), 0, packet.getLength());

                Matcher generalMatcher = GENERAL_MSG_PATTERN.matcher(rawMsg);
                if(!generalMatcher.matches()){
                    continue;
                }
                if (generalMatcher.group(2).equals("Error: Missing key")){
                    ui.deliverSystemMessage("Error: Server missing key");
                    continue;
                }

                boolean consumed = false;
                for (Iterator<Pattern> it = expectedTable.keySet().iterator(); it.hasNext(); ) {
                    Pattern p = it.next();
                    String msgTest = rawMsg;
                    if (expectEncrypted.get(p)){
                        msgTest = decrypt(rawMsg);
                    }
                    if (p.matcher(msgTest).matches()){
                        consumed = true;
                        expectedTableOut.put(p,msgTest);
                        lock.lock();
                        try {
                            expectedTable.get(p).signalAll();
                            it.remove();
                        }finally {
                            lock.unlock();
                        }
                    }
                }
                if (consumed){
                    continue;
                }

                rawMsg = decrypt(rawMsg);

                Message m = Message.fromRaw(rawMsg);
                if (m==null){
                    if (!generalMatcher.group(1).equals("server")){
                        continue;
                    }
                    ui.getReceiveQueue().add(new Message("server", generalMatcher.group(2), "", generalMatcher.group(3)));

                }else{
                    ui.getSendQueue().add(new Message(m.getDest(),"server",m.getId(),"Received"));
                    ui.getReceiveQueue().add(m);
                }


            } catch (IOException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            }
        }
    }

    protected String decrypt(String rawMsg) throws BadPaddingException, IllegalBlockSizeException {
        Matcher generalMatcher = GENERAL_MSG_PATTERN.matcher(rawMsg);
        generalMatcher.find();
        String source = generalMatcher.group(1);
        String dest = generalMatcher.group(2);
        String contents = generalMatcher.group(3);

        byte[] encryptedContents = Base64.getDecoder().decode(contents);
        byte[] decryptedContents = decryptCipher.doFinal(encryptedContents);
        contents = new String(decryptedContents);
        return source + "->" + dest + "#" + contents;
    }

    /**
     * wait for a message that matches the regex, unless the timeout happens first
     * @param regex
     * @param timeout
     * @return the message that matches
     */
    public String waitFor(String regex, long timeout, boolean encrypted){
        Pattern pattern = Pattern.compile(regex);
        Condition condition = lock.newCondition();
        expectedTable.put(pattern,condition);
        expectEncrypted.put(pattern, encrypted);
        lock.lock();
        try{
            condition.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
        return expectedTableOut.remove(pattern);
    }
}
