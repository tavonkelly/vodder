package me.tavon.vodder.stream;

public class Segment {

    private final int index;
    private final double length;
    private boolean downloaded;
    private boolean queued;

    public Segment(int index, double length) {
        this.index = index;
        this.length = length;
    }

    public int getIndex() {
        return index;
    }

    public double getLength() {
        return length;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }
}
