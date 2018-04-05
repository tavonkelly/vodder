package me.tavon.vodder.driver;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.util.HashSet;
import java.util.Set;

public class LiveMeDriver implements PlatformDriver {

    private OkHttpClient client;

    public LiveMeDriver(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public String getMasterPlaylist(String channelId, String streamId) throws Exception {
        Response response = getStreamInfo(streamId);
        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        String jsonData = responseBody.string();
        String url = new JsonParser().parse(jsonData).getAsJsonObject().getAsJsonObject("data")
                .getAsJsonObject("video_info").get("hlsvideosource").getAsString();

        if (url.isEmpty() || url.contains("playlist_eof")) {
            throw new Exception("user is not live");
        }

        return url;
    }

    @Override
    public Set<String> checkIfLive(String channelId) throws Exception {
        JsonObject data = getUserData(channelId);

        String status = data.get("status").getAsString();

        if (status.equals("1")) {
            HashSet<String> set = new HashSet<>();

            set.add(data.get("vid").getAsString());
            return new HashSet<>(set);
        }

        return new HashSet<>();
    }

    @Override
    public long getStreamStartTime(String channelId, String liveStreamId) throws Exception {
        JsonObject data = getUserData(channelId);

        return Long.parseLong(data.get("vtime").getAsString()) * 1000;
    }

    @Override
    public String getStreamTitle(String channelId, String liveStreamId) throws Exception {
        JsonObject data = getUserData(channelId);
        String title = data.get("title").getAsString();

        if (title.isEmpty()) {
            title = "Live.Me stream";
        }

        return title;
    }

    @Override
    public String getChannelDisplayName(String channelId) throws Exception {
        Response response = getStreamInfo(getUserData(channelId).get("vid").getAsString());
        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        String jsonData = responseBody.string();
        return new JsonParser().parse(jsonData).getAsJsonObject().getAsJsonObject("data")
                .getAsJsonObject("user_info").get("desc").getAsString();
    }

    private Response getStreamInfo(String streamId) throws Exception {
        MediaType mediaType = MediaType.parse("multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW");
        RequestBody body = RequestBody.create(mediaType, "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
                "Content-Disposition: form-data; name=\"userid\"\r\n\r\n1\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW" +
                "\r\nContent-Disposition: form-data; name=\"videoid\"\r\n\r\n" + streamId + "\r\n------WebKitF" +
                "ormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"area\"\r\n\r\n\r\n------" +
                "WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"h5\"\r\n\r\n1\r\n------" +
                "WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"vali\"\r\n\r\nKF7Dl2Z3c" +
                "mmk6RE\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--"); // TODO Clean this up
        Request request = new Request.Builder()
                .url("https://live.ksmobile.net/live/queryinfo")
                .post(body)
                .addHeader("content-type", "multipart/form-data; boundary=-" +
                        "---WebKitFormBoundary7MA4YWxkTrZu0gW")
                .addHeader("Cache-Control", "no-cache")
                .build();

        return client.newCall(request).execute();
    }

    private JsonObject getUserData(String channelId) throws Exception {
        Request request = new Request.Builder()
                .url("https://live.ksmobile.net/user/getlive?uid=" + channelId)
                .get()
                .addHeader("Cache-Control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            response.close();
            throw new Exception("Didn't get 200 or empty body");
        }

        return new JsonParser().parse(responseBody.string()).getAsJsonObject()
                .getAsJsonObject("data");
    }
}
