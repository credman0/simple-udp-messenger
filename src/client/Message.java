package client;

public class Message {
    private final String dest;
    private final String token;
    private final String id;
    private final String contents;

    public Message(String dest, String token, String id, String contents) {
        this.dest = dest;
        this.token = token;
        this.id = id;
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }

    public String getId() {
        return id;
    }

    public String getDest() {
        return dest;
    }

    public String getToken() {
        return token;
    }
}
