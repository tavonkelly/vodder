package me.tavon.vodder.driver;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.VideoListResponse;
import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.Format;
import com.iheartradio.m3u8.ParsingMode;
import com.iheartradio.m3u8.PlaylistParser;
import com.iheartradio.m3u8.data.Playlist;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YoutubeDriver implements PlatformDriver {

    private YouTube youtube;
    private OkHttpClient client;
    private static final String[] API_KEYS = new String[] {
            "AIzaSyABix56YSu77BHeLw88hmE8ZIwIR56x5Lc", // vodder
            "AIzaSyDueU0mfiy1MmylkhkvBd4mbTvKxNrI64I" // misc
    };
    private static int apiKeyIndex = 0;

    public YoutubeDriver(YouTube youtube, OkHttpClient client) {
        this.youtube = youtube;
        this.client = client;
    }

    @Override
    public String getMasterPlaylist(String channelId, String streamId) throws Exception {
        String masterUrl = null;
        String body = getVideoInfo(streamId);

        for (String s : body.split("&")) {
            if (s.startsWith("hlsvp")) {
                try {
                    masterUrl = URLDecoder.decode(s.split("=")[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }

        if (masterUrl == null) {
            throw new Exception("Could not get master url");
        }

        ResponseBody responseBody;
        Response response = client.newCall(new Request.Builder()
                .url(masterUrl).get().build()).execute();
        responseBody = response.body();

        if (responseBody == null) {
            throw new Exception("Could not load response body");
        }

        String masterPlaylist = responseBody.string();
        PlaylistParser parser;

        try (InputStream stream = new ByteArrayInputStream(masterPlaylist.getBytes(StandardCharsets.UTF_8.name()))) {
            parser = new PlaylistParser(stream, Format.EXT_M3U, Encoding.UTF_8
                    , ParsingMode.LENIENT);
        }

        Playlist playlist = parser.parse();

        return playlist.getMasterPlaylist().getPlaylists()
                .get(playlist.getMasterPlaylist().getPlaylists().size() - 1).getUri();
//        return playlist.getMasterPlaylist().getPlaylists()
//                .get(0).getUri();
    }

    @Override
    public Set<String> checkIfLive(String channelId) throws Exception {
        // Define the API request for retrieving search results.
        YouTube.Search.List search;
        try {
            search = youtube.search().list("id,snippet");
        } catch (IOException e) {
            throw new Exception("Could not create search", e);
        }

        search.setKey(API_KEYS[apiKeyIndex++]);

        if (apiKeyIndex >= API_KEYS.length) {
            apiKeyIndex = 0;
        }

        search.setChannelId(channelId);
        search.setEventType("live");
        search.setMaxResults(25L);

        // Restrict the search results to only include videos. See:
        // https://developers.google.com/youtube/v3/docs/search/list#type
        search.setType("video");

        // As a best practice, only retrieve the fields that the
        // application uses.
        search.setFields("items(id/videoId)");

        // Call the API and print results.
        SearchListResponse searchResponse;
        try {
            searchResponse = search.execute();
        } catch (IOException e) {
            throw new Exception("Could not execute search", e);
        }
        List<SearchResult> searchResultList = searchResponse.getItems();

        Set<String> ids = new HashSet<>();

        for (SearchResult searchResult : searchResultList) {
            ids.add(searchResult.getId().getVideoId());
        }

        return ids;
    }

    @Override
    public long getStreamStartTime(String channelId, String liveStreamId) throws Exception {
//        YouTube.Videos.List videosListByIdRequest = youtube.videos().list("snippet");
//
//        videosListByIdRequest.setKey(API_KEY);
//        videosListByIdRequest.setId(liveStreamId);
//
//        VideoListResponse response = videosListByIdRequest.execute();
//
//        if (response.getItems().size() == 0) {
//            throw new Exception("videos response is empty");
//        }
//
//        return response.getItems().get(0).getSnippet().getPublishedAt().getValue();


        String body = URLDecoder.decode(getVideoInfo(liveStreamId), "UTF-8");

        return Long.valueOf(body.split("lmt=")[1].split("&")[0].split(",")[0]) / 1000L;

//        return System.currentTimeMillis(); // TODO Youtube is so trash
    }

    @Override
    public String getStreamTitle(String channelId, String liveStreamId) throws Exception {
        YouTube.Videos.List videosListByIdRequest = youtube.videos().list("snippet");

        videosListByIdRequest.setKey(API_KEYS[apiKeyIndex++]);
        if (apiKeyIndex >= API_KEYS.length) {
            apiKeyIndex = 0;
        }
        videosListByIdRequest.setId(liveStreamId);

        VideoListResponse response = videosListByIdRequest.execute();

        if (response.getItems().size() == 0) {
            throw new Exception("videos response is empty");
        }

        return response.getItems().get(0).getSnippet().getTitle();
    }

    @Override
    public String getChannelDisplayName(String channelId) throws Exception {
        YouTube.Channels.List channelsListByIdRequest = youtube.channels().list("snippet");

        channelsListByIdRequest.setKey(API_KEYS[apiKeyIndex++]);
        if (apiKeyIndex >= API_KEYS.length) {
            apiKeyIndex = 0;
        }
        channelsListByIdRequest.setId(channelId);

        ChannelListResponse response = channelsListByIdRequest.execute();

        if (response.getItems().size() == 0) {
            throw new Exception("channels response is empty");
        }

        return response.getItems().get(0).getSnippet().getTitle();
    }

    private String getVideoInfo(String liveStreamId) throws Exception {
        Request request = new Request.Builder()
                .url("https://www.youtube.com/get_video_info?html5=1&video_id=" + liveStreamId)
                .get()
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (response.code() != 200 || body == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        return body.string();
    }
}
