package me.tavon.vodder.channel;

import com.google.api.services.youtube.model.Video;
import me.tavon.vodder.Vodder;
import me.tavon.vodder.stream.LiveStream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UploadWatcher implements Runnable {

    private Map<String, Long> uploadReadyTimeoutMap = new HashMap<>();
    private ExecutorService uploadThreadPool = Executors.newFixedThreadPool(3);
    private Set<String> activeChannels = new CopyOnWriteArraySet<>();

    private static final long UPLOAD_READY_WAIT = TimeUnit.MINUTES.toMillis(20);

    public UploadWatcher() {
        Thread thread = new Thread(this, "UploadWatcher");
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            for (Channel channel : Vodder.INSTANCE.getChannels()) {
                if (activeChannels.contains(channel.getChannelId())) {
                    continue;
                }

                for (LiveStream liveStream : channel.getLiveStreams()) {
                    if (liveStream.isUploaded()) {
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
                    activeChannels.add(channel.getChannelId());

                    uploadThreadPool.submit(() -> {
                        UploadJob uploadJob = new UploadJob(channel, liveStream, new UploadJob.JobCallback() {
                            @Override
                            public void onProgress(int partIndex, double progress) {
                                System.out.println(liveStream.getLiveStreamId() + " partIndex: " + partIndex
                                        + " Progress: " + (Math.round(progress * 10000D) / 100D) + "%");
                            }

                            @Override
                            public void onPartFinish(int partIndex, Video video) {
                                System.out.println("---------------------------------------");
                                System.out.println(liveStream.getLiveStreamId() + " partIndex: " + partIndex
                                        + " finished uploading. Video Id: " + video.getId());
                                System.out.println("---------------------------------------");
                            }

                            @Override
                            public void onFinish(List<Video> videos) {
                                System.out.println("---------------------------------------");
                                System.out.println(liveStream.getLiveStreamId() + " finished uploading. Video Ids: ");
                                for (Video video : videos) {
                                    System.out.println("     - " + video.getId());
                                }
                                System.out.println("---------------------------------------");

                                activeChannels.remove(channel.getChannelId());
                                liveStream.setUploaded(true);
                                liveStream.setReadyForUpload(false);
                            }

                            @Override
                            public void onException(Exception e) {
                                e.printStackTrace();

                                activeChannels.remove(channel.getChannelId());
                            }
                        });

                        uploadJob.start();
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
