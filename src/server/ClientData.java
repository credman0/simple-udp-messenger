package server;

import java.net.InetAddress;
import java.security.PublicKey;

class ClientData {

    protected String name;
    protected InetAddress addr;
    protected int port;
    protected String token;
    protected long lastSeen;
    protected PublicKey key;
    protected boolean loggedIn = false;

    public ClientData(String name, InetAddress addr, int port, String token) {
        this.name = name;
        this.addr = addr;
        this.port = port;
        this.token = token;
        updateLastSeen();
    }

    public String getName() {
        return name;
    }

    public InetAddress getAddr() {
        return addr;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public void updateLastSeen(){
        lastSeen = System.currentTimeMillis();
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public PublicKey getKey() {
        return key;
    }

    public void setKey(PublicKey key) {
        this.key = key;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

}
