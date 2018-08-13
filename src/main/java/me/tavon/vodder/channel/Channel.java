package me.tavon.vodder.channel;

import com.google.gson.Gson;
import me.tavon.vodder.Vodder;
import me.tavon.vodder.driver.PlatformDriver;
import me.tavon.vodder.driver.chat.PlatformChatDriver;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Title;
import me.tavon.vodder.stream.chat.ChatIngest;

import java.io.*;
import java.util.*;

public class Channel {

    private final String channelId;
    private final Platform platform;
    private final boolean publish;
    private transient PlatformDriver platformDriver;
    private transient PlatformChatDriver platformChatDriver;
    private transient List<LiveStream> liveStreams;

    public Channel(String channelId, Platform platform, boolean publish, PlatformDriver platformDriver) {
        Objects.requireNonNull(channelId, "channelId cannot be null");
        Objects.requireNonNull(platform, "platform cannot be null");
        Objects.requireNonNull(platformDriver, "platformDriver cannot be null");
        this.channelId = channelId;
        this.platform = platform;
        this.publish = publish;
        this.platformDriver = platformDriver;
        this.liveStreams = new LinkedList<>();
    }

    public String getChannelId() {
        return channelId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public boolean shouldPublish() {
        return publish;
    }

    public PlatformDriver getPlatformDriver() {
        return platformDriver;
    }

    public void setPlatformDriver(PlatformDriver platformDriver) {
        Objects.requireNonNull(platformDriver, "platformDriver cannot be null");
        this.platformDriver = platformDriver;
    }

    public PlatformChatDriver getPlatformChatDriver() {
        return platformChatDriver;
    }

    public void setPlatformChatDriver(PlatformChatDriver platformChatDriver) {
        Objects.requireNonNull(platformChatDriver, "platformChatDriver cannot be null");
        this.platformChatDriver = platformChatDriver;
    }

    public boolean isLive() {
        return !getActiveLiveStreams().isEmpty();
    }

    public List<LiveStream> getLiveStreams() {
        return Collections.unmodifiableList(liveStreams);
    }

    public void loadLiveStreamsFromFile() throws Exception {
        this.liveStreams = new LinkedList<>();

        File channelFolder = new File(getChannelDirectory());

        if (!channelFolder.exists()) {
            return;
        }

        File[] folders = channelFolder.listFiles();

        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            File dataFile = new File(folder, "data.json");

            if (!dataFile.exists()) {
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                LiveStream liveStream = Vodder.GSON.fromJson(reader, LiveStream.class);

                File chatFile = new File(folder, "chat.dat");

                if (chatFile.exists()) {
                    try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(chatFile))) {
                        try {
                            liveStream.setChatIngest((ChatIngest) inputStream.readObject());
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    liveStream.setChatIngest(new ChatIngest());
                }

                if (liveStream.isActive()) {
//                    platformChatDriver.onLiveStreamIngestStart(liveStream, liveStream.getChatIngest()); // TODO Test
                }

                this.liveStreams.add(liveStream);
            }
        }
    }

    public void saveLiveStreamsToFile(Gson gson) throws Exception {
        for (LiveStream liveStream : this.liveStreams) {
            liveStream.lockSegments();
            try {
                File file = new File(liveStream.getFileDirectory()).getParentFile();
                file.mkdirs();

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(file, "data.json")))) {
                    gson.toJson(liveStream, writer);
                }

                try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(
                        new File(file, "chat.dat")))) {
                    outputStream.writeObject(liveStream.getChatIngest());
                }
            } finally {
                liveStream.releaseSegments();
            }
        }
    }

    public String getChannelDirectory() {
        return "data/" + this.channelId + "/";
    }

    public List<LiveStream> getActiveLiveStreams() {
        List<LiveStream> streams = new ArrayList<>();

        for (LiveStream liveStream : this.liveStreams) {
            if (liveStream.isActive()) {
                streams.add(liveStream);
            }
        }

        return Collections.unmodifiableList(streams);
    }

    public String getMasterPlaylist(String liveStreamId) throws Exception {
        return platformDriver.getMasterPlaylist(this.channelId, liveStreamId);
    }

    public Set<String> checkIfLive() throws Exception {
        return platformDriver.checkIfLive(this.channelId);
    }

    public void createLiveStream(String liveStreamId, String title, long startTime, boolean active) throws Exception {
        LiveStream liveStream = new LiveStream(this.channelId, liveStreamId, new Title(title, startTime), startTime);

        if (active) {
            liveStream.setActive(true, this.platformChatDriver);
        }

        liveStreams.add(liveStream);
    }
}
