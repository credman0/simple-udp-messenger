package client;

import core.Message;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Random;

public class GUIFrontend implements ClientUI{
    @FXML
    TextArea chatArea;
    @FXML
    TextField destinationField;
    @FXML
    TextField messageField;
    @FXML
    TextField localPortField;
    @FXML
    TextField userIDField;
    @FXML
    PasswordField passwordField;
    @FXML
    Button loginButton;
    MessageQueue sendQueue;
    MessageQueue receiveQueue;
    ClientMessageHandler handler;
    Random rand = new Random();

    ChatLogManager chatLogManager = new ChatLogManager();

    public void start(){
        try {
            handler = new ClientMessageHandler(this);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.start();
        Thread receiveThread = new Thread(() -> {
            while(true) {
                if (!receiveQueue.isEmpty()) {
                    String text = chatArea.getText();
                    Message m = receiveQueue.remove();
                    chatLogManager.add(m.toString());
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        receiveThread.start();
        // set the button to fire on enter when focused
        loginButton.defaultButtonProperty().bind(loginButton.focusedProperty());

        Thread chatLogThread = new Thread(chatLogManager);
        chatLogThread.start();
    }

    public GUIFrontend(){
        sendQueue = new MessageQueue();
        receiveQueue = new MessageQueue();
    }
    @Override
    public InetAddress fetchServerIP() {
        try {
            return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int fetchServerPort() {
        return 12224;
    }

    @Override
    public String fetchUserID() {
        return userIDField.getText();
    }

    @Override
    public String fetchPassword() {
        return passwordField.getText();
    }

    @Override
    public MessageQueue getReceiveQueue() {
        return receiveQueue;
    }

    @Override
    public MessageQueue getSendQueue() {
        return sendQueue;
    }

    @Override
    public void deliverSystemMessage(String string) {
        chatLogManager.add(string);
    }

    @FXML
    public void addMessageToQueue(ActionEvent actionEvent) {
        String source = fetchUserID();
        String dest = destinationField.getText();
        String content = messageField.getText();
        messageField.clear();
        Message m = new Message(source,dest,rand,content);
        chatLogManager.add(m.toString());
        sendQueue.add(m);
    }

    @FXML
    public void login(ActionEvent actionEvent){
        if (handler.isConnected()){
            try {
                handler.logoff();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            handler.setUserID(fetchUserID());
            handler.attemptConnect(fetchPassword(), Integer.parseInt(localPortField.getText()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ChatLogManager implements Runnable {
        private ArrayDeque<String> queue = new ArrayDeque<>();
        private String text = "";

        @Override
        public void run() {
            while (true){
                if (!queue.isEmpty()){
                    synchronized (this) {
                        String addition = queue.remove();
                        text = text+addition+"\n";
                        chatArea.setText(text);
                        chatArea.selectPositionCaret(text.length());
                        chatArea.deselect();
                    }
                }else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void add(String string){
            synchronized (this){
                queue.add(string);
            }
        }
    }
}
