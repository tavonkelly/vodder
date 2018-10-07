package me.tavon.vodder.driver.chat;

import me.tavon.vodder.driver.chat.emote.EmoteRegistry;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.chat.ChatIngest;

import java.util.List;

public abstract class PlatformChatDriver {

    List<EmoteRegistry> emoteRegistries;

    public PlatformChatDriver(List<EmoteRegistry> emoteRegistries) {
        this.emoteRegistries = emoteRegistries;
    }

    abstract void onLiveStreamIngestStart(LiveStream liveStream, ChatIngest chatIngest) throws Exception;

    abstract void onLiveStreamIngestFinish(LiveStream liveStream) throws Exception;

}
