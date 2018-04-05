package me.tavon.vodder.channel;

import me.tavon.vodder.Vodder;
import me.tavon.vodder.stream.LiveStream;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UploadWatcher implements Runnable {

    private Map<String, Long> uploadReadyTimeoutMap = new HashMap<>();
    private ExecutorService uploadThreadPool = Executors.newFixedThreadPool(3);
    private Set<String> currentlyUploading = new CopyOnWriteArraySet<>();

    private static final long UPLOAD_READY_WAIT = TimeUnit.MINUTES.toMillis(20);

    public UploadWatcher() {
        Thread thread = new Thread(this, "UploadWatcher");
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            for (Channel channel : Vodder.INSTANCE.getChannels()) {
                for (LiveStream liveStream : channel.getLiveStreams()) {
                    if (liveStream.isUploaded()) {
                        continue;
                    }

                    if (currentlyUploading.contains(liveStream.getLiveStreamId())) {
                        continue;
                    }

                    if (!liveStream.isReadyForUpload()) {
                        uploadReadyTimeoutMap.remove(liveStream.getLiveStreamId());
                        continue;
                    }

                    if (!uploadReadyTimeoutMap.containsKey(liveStream.getLiveStreamId())) {
                        uploadReadyTimeoutMap.put(liveStream.getLiveStreamId(), System.currentTimeMillis());
                        continue;
                    }

                    if (uploadReadyTimeoutMap.get(liveStream.getLiveStreamId()) + UPLOAD_READY_WAIT >= System.currentTimeMillis()) {
                        continue;
                    }

                    uploadReadyTimeoutMap.remove(liveStream.getLiveStreamId());

                    File liveStreamDir = new File(channel.getChannelDirectory()
                            + liveStream.getLiveStreamId() + "/" + liveStream.getLiveStreamId() + ".ts");
                    File secondaryDir = new File(channel.getChannelDirectory()
                            + liveStream.getLiveStreamId() + "/" + liveStream.getLiveStreamId() + ".mp4");

                    if (!liveStreamDir.exists() && !secondaryDir.exists()) {
                        try {
                            liveStream.combineSegments(liveStreamDir, false);
                        } catch (Exception e) {
                            try {
                                throw new Exception("Could not upload livestream " + liveStream.getLiveStreamId(), e);
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                            continue;
                        }
                    }

                    if (!liveStreamDir.exists() && secondaryDir.exists()) {
                        liveStreamDir = secondaryDir;
                    }

                    currentlyUploading.add(liveStream.getLiveStreamId());

                    File finalLiveStreamDir = liveStreamDir;
                    uploadThreadPool.submit(() -> {
                        try {
                            UploadChannel.getUploadChannel(channel).uploadVideo(finalLiveStreamDir, channel, liveStream);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        } finally {
                            currentlyUploading.remove(liveStream.getLiveStreamId());
                        }

                        liveStream.setUploaded(true);
                        liveStream.setReadyForUpload(false);
                    });
                }
            }

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
