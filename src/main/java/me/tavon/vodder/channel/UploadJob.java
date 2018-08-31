package me.tavon.vodder.channel;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Segment;
import me.tavon.vodder.stream.Title;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UploadJob {

    private Channel channel;
    private LiveStream liveStream;
    private JobCallback jobCallback;

    private static final long MAX_PART_LENGTH_S = TimeUnit.HOURS.toSeconds(1);

    public UploadJob(Channel channel, LiveStream liveStream, JobCallback jobCallback) {
        this.channel = channel;
        this.liveStream = liveStream;
        this.jobCallback = jobCallback;
    }

    public void start() {
        if (this.liveStream.isActive()) {
            throw new IllegalArgumentException("Livestream " + this.liveStream.getLiveStreamId()
                    + " is currently active. Can't start upload.");
        }

        try {
            List<File> partFiles = this.combineSegments();
            UploadCredential uploadCredential = UploadCredential.getUploadCredential(this.channel);

            this.uploadVideo(partFiles, uploadCredential);
        } catch (Exception e) {
            jobCallback.onException(e);
        }
    }

    private void uploadVideo(List<File> partFiles, UploadCredential uploadCredential) throws Exception {
        YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(),
                uploadCredential.getCredential()).setApplicationName("vodder-upload-video").build();

        List<Video> completedUploads = new LinkedList<>();

        for (int i = 0; i < partFiles.size(); i++) {
            Video video = this.uploadPart(i, partFiles.get(i), youtube, partFiles.size() > 1);;

            completedUploads.add(video);
        }

        jobCallback.onFinish(completedUploads);
    }

    private Video uploadPart(int index, File partFile, YouTube youTube, boolean displayPart) throws Exception {
        Video video = new Video();
        VideoStatus status = new VideoStatus();

        if (this.channel.shouldPublish()) {
            status.setPrivacyStatus("public");
        } else {
            status.setPrivacyStatus("unlisted");
        }

        video.setStatus(status);

        VideoSnippet snippet = new VideoSnippet();

        Date date = new Date(this.liveStream.getStartTime());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        dateFormat.setTimeZone(TimeZone.getTimeZone("PST"));

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("PST"));
        cal.setTime(date);

        snippet.setTitle(displayPart ? ("PART " + (index + 1) + ": ") : "" + this.getMajorTitle(liveStream) + " ["
                + (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH)
                + "/" + cal.get(Calendar.YEAR) + "]");
        snippet.setDescription("(Automatic upload)\n\n" +
                this.channel.getPlatformDriver().getChannelDisplayName(this.channel.getChannelId()) + " live on "
                + this.channel.getPlatform().getDisplayName() + ": "
                + String.format(this.channel.getPlatform().getUrl(this.channel), this.channel.getChannelId()) + "\n"
                + "Stream started at " + dateFormat.format(date) + " (" + date.getTime() + ")");

        snippet.setTags(Arrays.asList("vodder", this.channel.getPlatform().getDisplayName()
                , this.channel.getPlatformDriver().getChannelDisplayName(this.channel.getChannelId())
                , "live", "livestream", "vod"));

        video.setSnippet(snippet);

        InputStreamContent mediaContent = new InputStreamContent("video/*",
                new BufferedInputStream(new FileInputStream(partFile)));
        YouTube.Videos.Insert videoInsert = youTube.videos()
                .insert("snippet,statistics,status", video, mediaContent);
        MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();

        uploader.setDirectUploadEnabled(false);

        MediaHttpUploaderProgressListener progressListener = listener -> {
            switch (listener.getUploadState()) {
                case MEDIA_IN_PROGRESS:
                    this.jobCallback.onProgress(index,
                            (double) listener.getNumBytesUploaded() / (double) partFile.length());
                    break;
            }
        };

        uploader.setProgressListener(progressListener);

        Video returnedVideo = videoInsert.execute();

        jobCallback.onPartFinish(index, returnedVideo);
        return returnedVideo;
    }

    private List<File> combineSegments() throws Exception {
        File partDir = new File(this.channel.getChannelDirectory()
                + this.liveStream.getLiveStreamId() + "/parts/");

        if (!partDir.mkdirs()) {
            throw new Exception("Could not create part directory " + partDir.getAbsolutePath());
        }

        this.liveStream.lockSegments();

        try {
            List<Map.Entry<String, Segment>> segFiles = new LinkedList<>();

            for (Segment segment : this.liveStream.getSegments()) {
                File segFile = new File(this.liveStream.getFileDirectory(), segment.getIndex() + ".ts");

                if (segFile.exists()) {
                    segFiles.add(new AbstractMap.SimpleEntry<>(segFile.getAbsolutePath(), segment));
                } else if (segment.isDownloaded()) {
                    throw new Exception("Could not find segment file for segment " + segment.getIndex());
                }
            }

            segFiles.sort(Comparator.comparingInt(item -> item.getValue().getIndex()));

            List<File> fileList = new LinkedList<>();
            File currFile;
            OutputStream out = null;
            int currPart = 0;
            long partLength = 0;
            byte[] buf = new byte[1000000];

            while (!segFiles.isEmpty()) {
                Map.Entry<String, Segment> entry = segFiles.remove(0);

                if (partLength + entry.getValue().getLength() >= MAX_PART_LENGTH_S) {
                    currPart++;
                    partLength = 0;

                    if (out != null) {
                        out.flush();
                        out.close();

                        out = null;
                    }
                }

                if (out == null) {
                    currFile = new File(partDir, this.liveStream.getLiveStreamId() + "-" + currPart + ".ts");
                    fileList.add(currFile);

                    if (!currFile.createNewFile()) {
                        throw new Exception("could not create file " + currFile.getAbsolutePath());
                    }

                    out = new FileOutputStream(currFile);
                }

                partLength += entry.getValue().getLength();

                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(entry.getKey()))) {
                    int b;
                    while ((b = in.read(buf)) >= 0) {
                        out.write(buf, 0, b);
                        out.flush();
                    }
                }
            }

            if (out != null) {
                out.flush();
                out.close();
            }

            return fileList;
        } finally {
            this.liveStream.releaseSegments();
        }
    }

    private String getMajorTitle(LiveStream liveStream) {
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

        return longest.getKey().getText();
    }

    public interface JobCallback {
        void onProgress(int partIndex, double progress);

        void onPartFinish(int partIndex, Video video);

        void onFinish(List<Video> videos);

        void onException(Exception e);
    }
}
