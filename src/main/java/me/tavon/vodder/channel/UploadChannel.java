package me.tavon.vodder.channel;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import me.tavon.vodder.Vodder;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Title;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants.AUTHORIZATION_SERVER_URL;
import static com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants.TOKEN_SERVER_URL;

public class UploadChannel {

    private Credential credential;

    private UploadChannel(Credential credential) {
        Objects.requireNonNull(credential, "credential cannot be null");
        this.credential = credential;
    }

    public void uploadVideo(File file, Channel channel, LiveStream liveStream) throws Exception {
        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), credential)
                .setApplicationName("youtube-cmdline-geolocationsearch-sample").build();

        Video video = new Video();

        VideoStatus status = new VideoStatus();

        if (channel.shouldPublish()) {
            status.setPrivacyStatus("public");
        } else {
            status.setPrivacyStatus("unlisted");
        }

        video.setStatus(status);

        VideoSnippet snippet = new VideoSnippet();

        Date date = new Date(liveStream.getStartTime());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        dateFormat.setTimeZone(TimeZone.getTimeZone("PST"));

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("PST"));
        cal.setTime(date);

        snippet.setTitle(getMajorTitle(liveStream) + " [" + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH) + "/" + cal.get(Calendar.YEAR) + "]");
        snippet.setDescription("(Automatic upload)\n\n" + channel.getPlatformDriver().getChannelDisplayName(channel.getChannelId()) + " live on "
                + channel.getPlatform().getDisplayName() + ": " + String.format(channel.getPlatform().getUrl(channel)
                , channel.getChannelId()) + "\n" + "Stream started at " + dateFormat.format(date) + " (" + date.getTime() + ")");

        snippet.setTags(Arrays.asList("vodder", channel.getPlatform().getDisplayName()
                , channel.getPlatformDriver().getChannelDisplayName(channel.getChannelId())
                , "live", "livestream", "vod"));

        video.setSnippet(snippet);

        InputStreamContent mediaContent = new InputStreamContent("video/*",
                new BufferedInputStream(new FileInputStream(file)));

        YouTube.Videos.Insert videoInsert = youtube.videos()
                .insert("snippet,statistics,status", video, mediaContent);

        MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();

        uploader.setDirectUploadEnabled(false);

        MediaHttpUploaderProgressListener progressListener = listener -> {
            switch (listener.getUploadState()) {
                case INITIATION_STARTED:
                    System.out.println("Initiation Started");
                    break;
                case INITIATION_COMPLETE:
                    System.out.println("Initiation Completed");
                    break;
                case MEDIA_IN_PROGRESS:
                    System.out.println("Upload in progress");
//                    System.out.println("getNumBytesUploaded " + listener.getNumBytesUploaded());
//                    System.out.println("file.length " + file.length());
                    System.out.println("Upload percentage: " + Math.round(((double) listener.getNumBytesUploaded() / (double) file.length()) * 100D) + "%");
                    break;
                case MEDIA_COMPLETE:
                    System.out.println("Upload Completed!");
                    break;
                case NOT_STARTED:
                    System.out.println("Upload Not Started!");
                    break;
            }
        };

        uploader.setProgressListener(progressListener);

        // Call the API and upload the video.
        Video returnedVideo = videoInsert.execute();

        // Print data about the newly inserted video from the API response.
        System.out.println("\n================== Returned Video ==================\n");
        System.out.println("  - Id: " + returnedVideo.getId());
        System.out.println("  - Title: " + returnedVideo.getSnippet().getTitle());
        System.out.println("  - Tags: " + returnedVideo.getSnippet().getTags());
        System.out.println("  - Privacy Status: " + returnedVideo.getStatus().getPrivacyStatus());
        System.out.println("  - View Count: " + returnedVideo.getStatistics().getViewCount());
    }

    private String getMajorTitle(LiveStream liveStream) throws Exception {
        List<Title> titleList = new ArrayList<>(liveStream.getLiveStreamTitles());

        Map.Entry<Title, Double> longest = null;

        for (int i = 0; i < titleList.size(); i++) {
            Title title = titleList.get(i);
            double start = title.getTimestamp();
            double end;

            if (i == titleList.size() - 1) {
                end = liveStream.getStartTime() + liveStream.getLiveStreamLength();
            } else {
                end = titleList.get(i + 1).getTimestamp();
            }

            double length = end - start;

            if (longest == null) {
                longest = new AbstractMap.SimpleEntry<>(title, length);
            } else if (longest.getValue() < length) {
                longest = new AbstractMap.SimpleEntry<>(title, length);
            }
        }

        if (longest == null) {
            throw new Exception("could not find major title");
        }

        return longest.getKey().getText();
    }

    public static UploadChannel getUploadChannel(Channel channel) throws Exception {
        File file = new File(Vodder.CHANNELS_PATH + channel.getChannelId() + "/StoredCredential");

        if (!file.exists() || !file.isFile()) {
            System.out.println("Requesting YouTube channel to upload VODs for " + channel.getChannelId());
        }

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken
                .authorizationHeaderAccessMethod(),
                new NetHttpTransport(),
                new JacksonFactory(),
                new GenericUrl(TOKEN_SERVER_URL),
                new ClientParametersAuthentication(
                        ChannelAuthConstants.API_KEY, ChannelAuthConstants.API_SECRET),
                ChannelAuthConstants.API_KEY,
                AUTHORIZATION_SERVER_URL).setScopes(Arrays.asList("https://www.googleapis.com/auth/youtube",
                "https://www.googleapis.com/auth/youtubepartner"))
                .setDataStoreFactory(new FileDataStoreFactory(file.getParentFile()))
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setHost(
                ChannelAuthConstants.DOMAIN).setPort(ChannelAuthConstants.PORT).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(channel.getChannelId());

        return new UploadChannel(credential);
    }
}
