package client;

import java.io.IOException;
import java.net.DatagramSocket;

public class ClientBackend extends Thread{
    protected DatagramSocket socket;
    protected ClientUI ui;

    public ClientBackend(DatagramSocket socket, ClientUI ui) {
        this.socket = socket;
        this.ui = ui;
    }

    public void run(){
        try {
            ClientMessageHandler handler = new ClientMessageHandler(ui.fetchUserID(), ui.getReceiveQueue(), ui.getSendQueue(),socket, ui.fetchServerIP(), ui.fetchServerPort());
            while(!handler.isConnected()){
                handler.attemptConnect(ui.fetchPassword());
                if (!handler.isConnected()){
                    Thread.sleep(500);
                }
            }
            handler.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
