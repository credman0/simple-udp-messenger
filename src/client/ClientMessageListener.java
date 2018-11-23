package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientMessageListener extends Thread{
    protected final MessageQueue receiveQueue;
    protected DatagramSocket socket;
    protected final Hashtable<Pattern, Condition> expectedTable = new Hashtable<>();
    protected final Hashtable<Pattern, String> expectedTableOut = new Hashtable<>();
    protected final Lock lock = new ReentrantLock();

    public ClientMessageListener(MessageQueue receiveQueue){
        this.receiveQueue = receiveQueue;
    }

    public void run(){
        byte[] buf = new byte[1024];
        while (true) {
            if (socket==null){
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

                boolean consumed = false;
                for (Iterator<Pattern> it = expectedTable.keySet().iterator(); it.hasNext(); ) {
                    Pattern p = it.next();
                    if (p.matcher(rawMsg).matches()){
                        consumed = true;
                        expectedTableOut.put(p,rawMsg);
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

                Message m = Message.fromRaw(rawMsg);
                if (m==null){
                    // TODO handle errors and confirmations
                    continue;
                }else{
                    receiveQueue.add(m);
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * wait for a message that matches the regex, unless the timeout happens first
     * @param regex
     * @param timeout
     * @return the message that matches
     */
    public String waitFor(String regex, long timeout){
        Pattern pattern = Pattern.compile(regex);
        Condition condition = lock.newCondition();
        expectedTable.put(pattern,condition);
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
