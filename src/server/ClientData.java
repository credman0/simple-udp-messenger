package server;

import java.net.InetAddress;

class ClientData {

    protected String name;
    protected InetAddress addr;
    protected int port;
    protected String token;
    protected long lastSeen;

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
}
