package client;

public class Message {
    private final String contents;
    private final String id;
    private final String dest;

    public Message(String contents, String id, String dest) {
        this.contents = contents;
        this.id = id;
        this.dest = dest;
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
}
