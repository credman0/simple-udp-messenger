package client;

import java.util.Random;

public class Message {
    private final String dest;
    private final String id;
    private final String contents;

    public Message(String dest, String id, String contents) {
        this.dest = dest;
        this.id = id;
        this.contents = contents;
    }

    public Message(String dest, Random rand, String contents) {
        this.dest = dest;
        this.id = generateID(rand);
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

    protected String generateID(Random rand){
        // random 10 digit number
        int id = rand.nextInt(1000000000);
        return String.format("%010d",id);
    }
}
