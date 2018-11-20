package client;

import java.net.InetAddress;

public interface ClientUI {
    InetAddress fetchServerIP();
    int fetchServerPort();
    String fetchUserID();
    String fetchPassword();
    MessageQueue getSendQueue();
}
