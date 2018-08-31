package me.tavon.vodder;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.tavon.vodder.channel.Channel;
import me.tavon.vodder.channel.UploadCredential;
import me.tavon.vodder.channel.UploadWatcher;
import me.tavon.vodder.driver.LiveMeDriver;
import me.tavon.vodder.driver.TwitchDriver;
import me.tavon.vodder.driver.YoutubeDriver;
import me.tavon.vodder.driver.chat.emote.BttvEmoteRegistry;
import me.tavon.vodder.driver.chat.emote.EmoteRegistry;
import me.tavon.vodder.driver.chat.emote.FrankerFaceZEmoteRegistry;
import me.tavon.vodder.driver.chat.emote.TwitchEmoteRegistry;
import me.tavon.vodder.driver.chat.YoutubeChatDriver;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Segment;
import me.tavon.vodder.stream.StreamWatcher;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Vodder {

    private Map<String, Channel> channelMap = new ConcurrentHashMap<>();
    private OkHttpClient httpClient = new OkHttpClient();
    private StreamWatcher streamWatcher;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String CHANNELS_PATH = "channels/";
    public static final String CACHE_PATH = "cache/";
    public static final String TWITCH_CLIENT_ID = "jzkbprff40iqj646a697cyrvl0zt2m6";
    public static final Logger LOGGER = Logger.getLogger(Vodder.class.getName());

    public static Vodder INSTANCE;

    private Vodder() {
        INSTANCE = this;
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);

        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tT %4$s: %5$s%6$s%n");

        LOGGER.info("----  Vodder  ----");

        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {
        }).setApplicationName("youtube-cmdline-geolocationsearch-sample").build();

        File cacheFolder = new File(CACHE_PATH);
        cacheFolder.mkdirs();

        List<EmoteRegistry> emoteRegistries = new ArrayList<>(Arrays.asList(
                new TwitchEmoteRegistry(cacheFolder, httpClient),
                new FrankerFaceZEmoteRegistry(cacheFolder, httpClient),
                new BttvEmoteRegistry(cacheFolder, httpClient)));

        for (int i = 0; i < emoteRegistries.size(); i++) {
            EmoteRegistry emoteRegistry = emoteRegistries.get(i);

            LOGGER.info("Loading " + emoteRegistry.getName() + " emotes...");
            try {
                emoteRegistry.init();
                LOGGER.info("Loaded " + emoteRegistry.getEmoteMap().size() + " "
                        + emoteRegistry.getName() + " emote(s)");
            } catch (Exception e) {
                emoteRegistries.remove(i);
                LOGGER.warning("Could not load " + emoteRegistry.getName()
                        + " emotes because: " + e.getMessage());
            }
        }

        emoteRegistries = Collections.unmodifiableList(emoteRegistries);

        File folder = new File(CHANNELS_PATH);
        folder.mkdirs();

        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }

            try (FileReader reader = new FileReader(file)) {
                Channel channel = GSON.fromJson(reader, Channel.class);

                switch (channel.getPlatform()) {
                    case YOUTUBE:
                        channel.setPlatformDriver(new YoutubeDriver(youtube, httpClient));
                        channel.setPlatformChatDriver(new YoutubeChatDriver(httpClient, emoteRegistries));
                        break;
                    case TWITCH:
                        channel.setPlatformDriver(new TwitchDriver(httpClient));
                        break;
                    case LIVE_ME:
                        channel.setPlatformDriver(new LiveMeDriver(httpClient));
                }

                channel.loadLiveStreamsFromFile();
                channelMap.put(channel.getChannelId(), channel);

                UploadCredential.getUploadCredential(channel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        LOGGER.info("Loaded " + channelMap.size() + " channel(s)");

//        for (Channel channel : channelMap.values()) {
//            LiveStream liveStream = channel.getLiveStreams().get(0);
//            File liveStreamDir = new File(channel.getChannelDirectory()
//                    + liveStream.getLiveStreamId() + "/" + liveStream.getLiveStreamId() + ".ts");
//            ChatEncoder chatEncoder = new ChatEncoder(liveStream, liveStreamDir, httpClient);
//
//            try {
//                chatEncoder.start();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            System.out.println("done");
//        }

        streamWatcher = new StreamWatcher(this);
        new UploadWatcher();

        TimerTask saveTask = new TimerTask() {
            @Override
            public void run() {
                LOGGER.fine("Saving data...");
                for (Channel channel : channelMap.values()) {
                    try {
                        channel.saveLiveStreamsToFile(GSON);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        new Timer("SaveTimer").schedule(saveTask,
                TimeUnit.SECONDS.toMillis(30), TimeUnit.MINUTES.toMillis(5));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down...");

            try {
                streamWatcher.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (Channel channel : this.getChannels()) {
                for (LiveStream liveStream : channel.getActiveLiveStreams()) {
                    for (Segment segment : liveStream.getSegments()) {
                        if (!segment.isDownloaded()) {
                            segment.setQueued(false);
                        }
                    }
                }
            }

            saveTask.run();
        }));
    }

    public List<Channel> getChannels() {
        return new ArrayList<>(channelMap.values());
    }

    public Channel getChannel(String channelId) throws Exception {
        if (channelId == null) {
            throw new Exception("null channelId");
        }

        Channel channel = channelMap.get(channelId);

        if (channel == null) {
            throw new Exception("channel not found");
        }

        return channel;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public StreamWatcher getStreamWatcher() {
        return streamWatcher;
    }

    public static void main(String[] args) throws Exception {
        new Vodder();
    }
}
