package me.tavon.vodder.stream;

public class Title {

    private final String text;
    private final long timestamp;

    public Title(String text, long timestamp) {
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
