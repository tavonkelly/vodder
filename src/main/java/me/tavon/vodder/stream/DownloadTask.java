package me.tavon.vodder.stream;

import me.tavon.vodder.Vodder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.*;

public class DownloadTask implements Runnable {

    private OkHttpClient client;
    private LiveStream liveStream;
    private Segment segment;
    private String downloadUrl;
    private File destinationFile;

    public DownloadTask(OkHttpClient client, LiveStream liveStream, Segment segment, String downloadUrl
            , File destinationFile) {
        this.client = client;
        this.liveStream = liveStream;
        this.segment = segment;
        this.downloadUrl = downloadUrl;
        this.destinationFile = destinationFile;
    }

    @Override
    public void run() {
        if (!Vodder.INSTANCE.getStreamWatcher().isRunning()) {
            dequeueSegment();
            return;
        }

        Response response;
        try {
            response = client.newCall(new Request.Builder()
                    .url(downloadUrl).get().build()).execute();
        } catch (IOException e) {
            dequeueSegment();
            Vodder.LOGGER.warning("Could not download segment " + segment.getIndex() + ": " + e.getMessage());
            return;
        }

        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            dequeueSegment();
            Vodder.LOGGER.warning("Could not download segment " + segment.getIndex()
                    + ". Received non-200 or empty body");
            return;
        }

        destinationFile.getParentFile().mkdirs();
        try {
            destinationFile.createNewFile();
        } catch (IOException e) {
            dequeueSegment();
            Vodder.LOGGER.warning("Could not create destination file " + destinationFile.getName()
                    + ": " + e.getMessage());
            return;
        }

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destinationFile))) {
            InputStream inputStream = responseBody.byteStream();
            byte[] buffer = new byte[8 * 1024];
            int bytesRead = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            Vodder.LOGGER.warning("Could not write from byteStream to destination file "
                    + destinationFile.getName() + ": " + e.getMessage());
        }

        liveStream.lockSegments();
        try {
            segment.setDownloaded(true);
            segment.setQueued(false);
        } finally {
            liveStream.releaseSegments();
        }
    }

    private void dequeueSegment() {
        liveStream.lockSegments();
        try {
            segment.setQueued(false);
        } finally {
            liveStream.releaseSegments();
        }
    }
}
