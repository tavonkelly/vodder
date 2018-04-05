package me.tavon.vodder.driver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.Format;
import com.iheartradio.m3u8.ParsingMode;
import com.iheartradio.m3u8.PlaylistParser;
import me.tavon.vodder.Vodder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TwitchDriver implements PlatformDriver {

    private OkHttpClient client;
    private String cachedDisplayName;
    private long cachedTimestamp;

    private static final long CACHE_TIMEOUT = TimeUnit.MINUTES.toMillis(10);

    public TwitchDriver(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public String getMasterPlaylist(String channelId, String streamId) throws Exception {
        Response response = getPlaylistResponse(getChannelDisplayName(channelId));
        ResponseBody body = response.body();

        if (response.code() != 200 || body == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        String masterPlaylist = body.string();
        PlaylistParser parser;

        try (InputStream stream = new ByteArrayInputStream(masterPlaylist.getBytes(StandardCharsets.UTF_8.name()))) {
            parser = new PlaylistParser(stream, Format.EXT_M3U, Encoding.UTF_8
                    , ParsingMode.LENIENT);
        }

        return parser.parse().getMasterPlaylist().getPlaylists().get(0).getUri();
    }

    @Override
    public Set<String> checkIfLive(String channelId) throws Exception {
        JsonArray array = getLiveStreamsObjs(channelId);

        if (array.isJsonNull() || array.size() == 0) {
            return new HashSet<>();
        }

        Set<String> hashSet = new HashSet<>();

        Iterator<JsonElement> elementIterator = array.iterator();
        JsonElement element;

        while (elementIterator.hasNext()) {
            element = elementIterator.next();

            hashSet.add(element.getAsJsonObject().get("id").getAsString());
        }

        return hashSet;
    }

    @Override
    public long getStreamStartTime(String channelId, String liveStreamId) throws Exception {
        JsonArray array = getLiveStreamsObjs(channelId);

        if (array.isJsonNull() || array.size() == 0) {
            throw new Exception("stream objects is empty");
        }

        Iterator<JsonElement> elementIterator = array.iterator();
        JsonObject object;

        while (elementIterator.hasNext()) {
            object = elementIterator.next().getAsJsonObject();

            String id = object.getAsJsonObject().get("id").getAsString();

            if (id.equalsIgnoreCase(liveStreamId)) {
                String timestamp = object.get("started_at").getAsString();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("GMT"));

                return format.parse(timestamp).getTime();
            }
        }

        throw new Exception("did not find stream id");
    }

    @Override
    public String getStreamTitle(String channelId, String liveStreamId) throws Exception {
        JsonArray array = getLiveStreamsObjs(channelId);

        if (array.isJsonNull() || array.size() == 0) {
            throw new Exception("stream objects is empty");
        }

        Iterator<JsonElement> elementIterator = array.iterator();
        JsonObject object;

        while (elementIterator.hasNext()) {
            object = elementIterator.next().getAsJsonObject();

            String id = object.getAsJsonObject().get("id").getAsString();

            if (id.equalsIgnoreCase(liveStreamId)) {
                return object.get("title").getAsString();
            }
        }

        throw new Exception("did not find stream id");
    }

    @Override
    public String getChannelDisplayName(String channelId) throws Exception {
        if (cachedDisplayName != null && cachedTimestamp + CACHE_TIMEOUT > System.currentTimeMillis()) {
            return cachedDisplayName;
        }

        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/users?id=" + channelId)
                .get()
                .addHeader("client-id", Vodder.TWITCH_CLIENT_ID)
                .addHeader("cache-control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (response.code() != 200 || body == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        JsonArray jsonArray = new JsonParser().parse(body.string()).getAsJsonObject()
                .getAsJsonArray("data");

        if (jsonArray.isJsonNull() || jsonArray.size() == 0) {
            throw new Exception("json array is empty");
        }

        String displayName = jsonArray.get(0).getAsJsonObject().get("display_name").getAsString();

        cachedDisplayName = displayName;
        cachedTimestamp = System.currentTimeMillis();

        return displayName;
    }

    private JsonArray getLiveStreamsObjs(String channelId) throws Exception {
        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/streams?user_id=" + channelId + "&type=live")
                .get()
                .addHeader("client-id", Vodder.TWITCH_CLIENT_ID)
                .addHeader("cache-control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (response.code() != 200 || body == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        return new JsonParser().parse(body.string()).getAsJsonObject().getAsJsonArray("data");
    }

    private Response getPlaylistResponse(String channelName) throws Exception {
        Request request = new Request.Builder()
                .url("https://api.twitch.tv/api/channels/" + channelName + "/access_token")
                .get()
                .addHeader("client-id", Vodder.TWITCH_CLIENT_ID)
                .addHeader("cache-control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (response.code() != 200 || body == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        JsonObject jsonObject = new JsonParser().parse(body.string()).getAsJsonObject();
        String token = URLEncoder.encode(jsonObject.get("token").getAsString(), "UTF-8");
        String signature = URLEncoder.encode(jsonObject.get("sig").getAsString(), "UTF-8");

        request = new Request.Builder()
                .url("https://usher.ttvnw.net/api/channel/hls/" + channelName.toLowerCase() + ".m3u8?sig="
                        + signature + "&token=" + token)
                .get()
                .addHeader("cache-control", "no-cache")
                .build();

        return client.newCall(request).execute();
    }
}
