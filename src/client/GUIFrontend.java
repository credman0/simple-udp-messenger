package client;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;

public class GUIFrontend extends Application implements ClientUI {
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
    MessageQueue sendQueue;
    MessageQueue receiveQueue;
    DatagramSocket socket;
    ClientBackend backend;
    Random rand = new Random();
    public static void main (String[] args){
        launch(args);
    }
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("GUI.fxml"));
        Scene scene = new Scene(root);

        stage.setTitle("UDP Chat");
        stage.setScene(scene);
        stage.show();
//        try {
//            socket = new DatagramSocket(Integer.parseInt(localPortField.getText()), fetchServerIP());
//            backend = new ClientBackend(socket, this);
//        } catch (SocketException e) {
//            e.printStackTrace();
//        }
//        backend.start();
    }

    public void init(){
        new Thread(() -> {
            while(true) {
                System.out.println(chatArea);
            }
        }).start();
        backend = null;
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

    @FXML
    public void addMessageToQueue(ActionEvent actionEvent) {
        System.out.println("Added");
        String source = fetchUserID();
        String dest = destinationField.getText();
        String content = messageField.getText();
        messageField.clear();
        sendQueue.add(new Message(source,dest,rand,content));
    }
}
