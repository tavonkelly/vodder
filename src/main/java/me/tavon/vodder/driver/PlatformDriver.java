package me.tavon.vodder.driver;

import java.util.Set;

public interface PlatformDriver {

    String getMasterPlaylist(String channelId, String streamId) throws Exception;

    Set<String> checkIfLive(String channelId) throws Exception;

    long getStreamStartTime(String channelId, String liveStreamId) throws Exception;

    String getStreamTitle(String channelId, String liveStreamId) throws Exception;

    String getChannelDisplayName(String channelId) throws Exception;
}
