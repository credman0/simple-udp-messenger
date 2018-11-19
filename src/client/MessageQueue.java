package client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class MessageQueue {
    protected ArrayList<ActionListener> listeners = new ArrayList<>();
    protected ArrayDeque<Message>  queue =  new ArrayDeque<>();
    public void addActionListener(ActionListener e){
        listeners.add(e);
    }

    public void add(Message m){
        queue.add(m);
        listeners.parallelStream().forEach(e->e.actionPerformed(new ActionEvent(m,ActionEvent.ACTION_PERFORMED,"add")));
    }

    public Message remove(){
        Message m = queue.remove();
        listeners.parallelStream().forEach(e->e.actionPerformed(new ActionEvent(m,ActionEvent.ACTION_PERFORMED,"remove")));
        return m;
    }

    public boolean isEmpty(){
        return queue.isEmpty();
    }
}
