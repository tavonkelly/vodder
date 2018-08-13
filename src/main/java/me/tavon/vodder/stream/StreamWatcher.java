package me.tavon.vodder.stream;

import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.Format;
import com.iheartradio.m3u8.ParsingMode;
import com.iheartradio.m3u8.PlaylistParser;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.TrackData;
import me.tavon.vodder.Vodder;
import me.tavon.vodder.channel.Channel;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StreamWatcher implements Runnable {

    private Vodder vodder;
    private Map<String, Long> channelTimeMap = new HashMap<>();
    private Map<String, Long> livestreamDownloadTimeMap = new HashMap<>();
    private Map<String, Long> liveStreamTitleTimeMap = new HashMap<>();
    private ExecutorService currSegThreadPool = Executors.newFixedThreadPool(4);
    private ExecutorService pastSegThreadPool = Executors.newFixedThreadPool(4);

    private Thread thread;
    private boolean running = true;

    private static final long LIVE_CHECK_SLEEP_TIME = TimeUnit.MINUTES.toMillis(5);
    private static final long DOWNLOAD_SLEEP_TIME = TimeUnit.SECONDS.toMillis(10);
    private static final long PAST_SEG_THRESHOLD = TimeUnit.MINUTES.toMillis(5);
    private static final long TITLE_CHECK_SLEEP_TIME = TimeUnit.MINUTES.toMillis(5);
    private static final long ACTIVE_LIVE_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

    public StreamWatcher(Vodder vodder) {
        this.vodder = vodder;

        this.thread = new Thread(this, "StreamWatcher");
        this.thread.start();
    }

    public void shutdown() throws Exception {
        this.running = false;
        currSegThreadPool.shutdown();
        pastSegThreadPool.shutdown();
        this.thread.join();
    }

    boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        int index = 0;

        while (running) {
            if (index >= vodder.getChannels().size()) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                index = 0;
            }

            Channel channel = vodder.getChannels().get(index++);

            if (channelTimeMap.getOrDefault(channel.getChannelId(), 0L) >= System.currentTimeMillis()) {
                continue;
            }

            Set<String> streamIds;

            try {
                streamIds = channel.checkIfLive();
            } catch (Exception e) {
                Vodder.LOGGER.warning("Could not check if channel " + channel.getChannelId()
                        + " is live: " + e.getMessage());
                continue;
            }

            outerLoop:
            for (String s : streamIds) {
                for (LiveStream liveStream : channel.getLiveStreams()) {
                    if (liveStream.getLiveStreamId().equals(s)) {
                        if (!liveStream.isActive()) {
                            try {
                                liveStream.setActive(true, channel.getPlatformChatDriver());
                                Vodder.LOGGER.info("Livestream " + liveStream.getLiveStreamId()
                                        + " is now active");
                            } catch (Exception e) {
                                Vodder.LOGGER.warning("Could not set livestream to active for " + s
                                        + ": " + e.getMessage());
                            }
                        }

                        continue outerLoop;
                    }
                }

                long startTime;
                try {
                    startTime = channel.getPlatformDriver().getStreamStartTime(channel.getChannelId(), s);
                } catch (Exception e) {
                    Vodder.LOGGER.warning("Could not get stream start time for " + s + ": " + e.getMessage());
                    continue;
                }

                String streamTitle;
                try {
                    streamTitle = channel.getPlatformDriver().getStreamTitle(channel.getChannelId(), s);
                } catch (Exception e) {
                    Vodder.LOGGER.warning("Could not get stream title for " + s + ": " + e.getMessage());
                    continue;
                }

                try {
                    channel.createLiveStream(s, streamTitle, startTime, true);
                    Vodder.LOGGER.info("Created livestream " + s + " for " + channel.getChannelId());
                } catch (Exception e) {
                    Vodder.LOGGER.warning("Could not create livestream " + s + ": " + e.getMessage());
                }
            }

            if (channel.isLive()) {
                channelTimeMap.put(channel.getChannelId(), System.currentTimeMillis() + DOWNLOAD_SLEEP_TIME);
            } else {
                channelTimeMap.put(channel.getChannelId(), System.currentTimeMillis() + LIVE_CHECK_SLEEP_TIME);
                continue;
            }

            for (LiveStream liveStream : channel.getActiveLiveStreams()) {
                if (livestreamDownloadTimeMap.containsKey(liveStream.getLiveStreamId())) {
                    if (livestreamDownloadTimeMap.get(liveStream.getLiveStreamId()) + ACTIVE_LIVE_TIMEOUT
                            < System.currentTimeMillis()) {
                        try {
                            liveStream.setActive(false, channel.getPlatformChatDriver());
                            livestreamDownloadTimeMap.remove(liveStream.getLiveStreamId());
                            Vodder.LOGGER.info("Livestream " + liveStream.getLiveStreamId() + " is no longer active");
                        } catch (Exception e) {
                            Vodder.LOGGER.warning("Could not set stream non-active for "
                                    + liveStream.getLiveStreamId() + ": " + e.getMessage());
                        }

                        continue;
                    }
                } else {
                    livestreamDownloadTimeMap.put(liveStream.getLiveStreamId(), System.currentTimeMillis());
                }

                if (liveStreamTitleTimeMap.getOrDefault(liveStream.getLiveStreamId(), 0L) < System.currentTimeMillis()) {
                    liveStreamTitleTimeMap.put(liveStream.getLiveStreamId(), System.currentTimeMillis() + TITLE_CHECK_SLEEP_TIME);

                    String title;

                    try {
                        title = channel.getPlatformDriver().getStreamTitle(channel.getChannelId(), liveStream.getLiveStreamId());
                    } catch (Exception e) {
                        Vodder.LOGGER.warning("Could not get stream title for " + liveStream.getLiveStreamId()
                                + ": " + e.getMessage());
                        continue;
                    }

                    if (title == null) {
                        Vodder.LOGGER.warning("Got null stream title for " + liveStream.getLiveStreamId());
                        continue;
                    }

                    if (!title.equals(liveStream.getCurrentTitle().getText())) {
                        liveStream.addNewLiveStreamTitle(new Title(title, System.currentTimeMillis()));
                    }
                }

                int segments;

                try {
                    segments = checkPlaylistAndDownload(channel, liveStream);
                } catch (Exception e) {
                    Vodder.LOGGER.warning("Could not check stream playlist for " + liveStream.getLiveStreamId()
                            + ": " + e.getMessage());
                    continue;
                }

                if (segments > 0) {
                    livestreamDownloadTimeMap.put(liveStream.getLiveStreamId(), System.currentTimeMillis());
                }
            }
        }
    }

    private int checkPlaylistAndDownload(Channel channel, LiveStream liveStream) throws Exception {
        String playlistUri = channel.getMasterPlaylist(liveStream.getLiveStreamId());
        Response response = vodder.getHttpClient().newCall(new Request.Builder()
                .url(playlistUri).get().build()).execute();

        ResponseBody responseBody = response.body();

        if (responseBody == null) {
            throw new Exception("Could not load response body");
        }

        PlaylistParser parser = new PlaylistParser(responseBody.byteStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
        Playlist playlist = parser.parse();

        if (!playlist.getMediaPlaylist().isOngoing()) {
            liveStream.setActive(false, channel.getPlatformChatDriver());
            Vodder.LOGGER.info("Livestream " + liveStream.getLiveStreamId() + " is no longer active");
        }

        response.close();

        int currSeq = playlist.getMediaPlaylist().getMediaSequenceNumber();
        int segCount = 0;

        liveStream.lockSegments();
        try {
            outerLoop:
            for (TrackData trackData : playlist.getMediaPlaylist().getTracks()) {
                for (Segment segment : liveStream.getSegments()) {
                    if (segment.getIndex() == currSeq && (segment.isDownloaded() || segment.isQueued())) {
                        currSeq++;
                        continue outerLoop;
                    }
                }

                long lengthSoFar = TimeUnit.SECONDS.toMillis(Math.round(liveStream.getLiveStreamLength(currSeq)));
                Segment segment = new Segment(currSeq, trackData.getTrackInfo().duration);

                segment.setQueued(true);
                liveStream.getSegments().add(segment);

                ExecutorService threadPool;

                if (liveStream.getStartTime() + lengthSoFar <= System.currentTimeMillis() - PAST_SEG_THRESHOLD) {
                    threadPool = pastSegThreadPool;
                } else {
                    threadPool = currSegThreadPool;
                }

                String uri = trackData.getUri();

                if (!uri.startsWith("https:") && !uri.startsWith("http:")) { // Handle relative urls
                    uri = playlistUri.substring(0, playlistUri.lastIndexOf("/") + 1) + uri;
                }

                threadPool.submit(new DownloadTask(vodder.getHttpClient(), liveStream, segment, uri
                        , new File(liveStream.getFileDirectory() + currSeq + ".ts")));
                segCount++;
                currSeq++;
            }

            if (segCount != 0) {
                int queueCount = 0;

                for (Segment segment : liveStream.getSegments()) {
                    if (segment.isQueued()) {
                        queueCount++;
                    }
                }

                Vodder.LOGGER.info("Queued " + segCount + " segment(s) for " + channel.getChannelId()
                        + " (" + liveStream.getLiveStreamId() + "). Total " + queueCount + " segment(s) queued");
            }

            return segCount;
        } finally {
            liveStream.releaseSegments();
        }
    }
}
