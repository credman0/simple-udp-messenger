package core;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Message {
    private final String source;
    private final String dest;
    private final String id;
    private final String contents;

    protected static final String MSG_REGEX = "(\\S+)-\\>(\\S+)#\\<(.*)\\>\\<(.*)\\>(.*)";
    protected static final Pattern MSG_PATTERN = Pattern.compile(MSG_REGEX);

    public Message(String source, String dest, String id, String contents) {
        this.source = source;
        this.dest = dest;
        this.id = id;
        this.contents = contents;
    }

    public Message(String source, String dest, Random rand, String contents) {
        this.source = source;
        this.dest = dest;
        this.id = generateID(rand);
        this.contents = contents;
    }

    public static Message fromRaw(String raw){
        Matcher matcher = MSG_PATTERN.matcher(raw);
        if (!matcher.find()){
            return null;
        }
        String source = matcher.group(1);
        String dest = matcher.group(2);
        String id = matcher.group(4);
        String contents = matcher.group(5);
        return new Message(source,dest,id,contents);
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

    public String getSource() {
        return source;
    }

    public String toString(){
        return getSource()+"->"+getDest()+": "+getContents();
    }
}
