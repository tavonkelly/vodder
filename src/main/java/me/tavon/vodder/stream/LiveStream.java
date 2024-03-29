package me.tavon.vodder.stream;

import me.tavon.vodder.Vodder;
import me.tavon.vodder.driver.chat.PlatformChatDriver;
import me.tavon.vodder.stream.chat.ChatIngest;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LiveStream {

    private final String channelId;
    private final String liveStreamId;
    private Set<Title> liveStreamTitles;
    private final long startTime;
    private final List<Segment> segments;
    private transient Lock segmentsLock;
    private transient ChatIngest chatIngest;
    private boolean active;
    private boolean readyForUpload;
    private boolean uploaded;

    public LiveStream(String channelId, String liveStreamId, Title title, long startTime) {
        Objects.requireNonNull(channelId, "channelId cannot be null");
        Objects.requireNonNull(liveStreamId, "liveStreamId cannot be null");
        this.channelId = channelId;
        this.liveStreamId = liveStreamId;
        this.startTime = startTime;
        this.liveStreamTitles = new LinkedHashSet<>();
        this.liveStreamTitles.add(title);
        this.segments = new LinkedList<>();
        this.chatIngest = new ChatIngest();
    }

    public String getLiveStreamId() {
        return liveStreamId;
    }

    public Set<Title> getLiveStreamTitles() {
        return liveStreamTitles;
    }

    public Title getCurrentTitle() {
        return liveStreamTitles.toArray(new Title[0])[liveStreamTitles.size() - 1];
    }

    public void addNewLiveStreamTitle(Title title) {
        this.liveStreamTitles.add(title);
    }

    public long getStartTime() {
        return startTime;
    }

    public void lockSegments() {
        if (segmentsLock == null) {
            segmentsLock = new ReentrantLock(true);
        }

        segmentsLock.lock();
    }

    public void releaseSegments() {
        if (segmentsLock == null) {
            segmentsLock = new ReentrantLock(true);
        }

        segmentsLock.unlock();
    }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public double getLiveStreamLength() {
        if (this.segments.size() == 0) {
            return 0D;
        }

        return getLiveStreamLength(this.segments.get(this.segments.size() - 1).getIndex());
    }

    // TODO This will break if segments are out of order
    public double getLiveStreamLength(int maxSeq) {
        double totalLength = 0D;
        Segment lastSegment = null;

        for (Segment segment : this.segments) {
            if (segment.getIndex() > maxSeq) {
                return totalLength;
            }

            if (lastSegment == null && segment.getIndex() != 0) {
                totalLength += segment.getLength() * segment.getIndex();
            } else if (lastSegment != null && segment.getIndex() != lastSegment.getIndex() + 1) {
                totalLength += (segment.getIndex() - lastSegment.getIndex()) * lastSegment.getLength();
            }

            totalLength += segment.getLength();
            lastSegment = segment;
        }

        return totalLength;
    }

    public ChatIngest getChatIngest() {
        return chatIngest;
    }

    public void setChatIngest(ChatIngest chatIngest) {
        this.chatIngest = chatIngest;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active, PlatformChatDriver chatDriver) throws Exception {
        if (this.uploaded) {
            throw new Exception("vod already uploaded!");
        }

        if (!active && this.active) {
            this.readyForUpload = true;
//            chatDriver.onLiveStreamIngestFinish(this); // TODO Test
        }

        if (active && !this.active) {
            this.readyForUpload = false;
//            chatDriver.onLiveStreamIngestStart(this, this.chatIngest); // TODO Test
        }

        this.active = active;
    }

    public boolean isReadyForUpload() {
        return readyForUpload;
    }

    public void setReadyForUpload(boolean readyForUpload) {
        this.readyForUpload = readyForUpload;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public String getFileDirectory() throws Exception {
        return Vodder.INSTANCE.getChannel(this.channelId).getChannelDirectory() + this.liveStreamId + "/segments/";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LiveStream that = (LiveStream) o;

        if (startTime != that.startTime) return false;
        if (active != that.active) return false;
        if (!channelId.equals(that.channelId)) return false;
        if (!liveStreamId.equals(that.liveStreamId)) return false;
        if (!segments.equals(that.segments)) return false;
        return segmentsLock != null ? segmentsLock.equals(that.segmentsLock) : that.segmentsLock == null;
    }

    @Override
    public int hashCode() {
        int result = channelId.hashCode();
        result = 31 * result + liveStreamId.hashCode();
        result = 31 * result + (int) (startTime ^ (startTime >>> 32));
        result = 31 * result + segments.hashCode();
        result = 31 * result + (segmentsLock != null ? segmentsLock.hashCode() : 0);
        result = 31 * result + (active ? 1 : 0);
        return result;
    }
}
