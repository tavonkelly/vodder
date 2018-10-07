package me.tavon.vodder.encoding;

import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Segment;
import me.tavon.vodder.stream.chat.ChatMessage;
import okhttp3.OkHttpClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChatEncoder {

    private LiveStream liveStream;
    private File preChatCombinedFile;
    private ImageCache imageCache;

    private static final float SCALE = 1f;
    private static final int IMAGE_WIDTH = 360;
    private static final int IMAGE_HEIGHT = 1080;
    private static final int FRAMES_PER_SECOND = 10; // Must be a factor of 1000

    public ChatEncoder(LiveStream liveStream, File preChatCombinedFile, OkHttpClient httpClient) {
        this.liveStream = liveStream;
        this.preChatCombinedFile = preChatCombinedFile;
        this.imageCache = new ImageCache(httpClient);
    }

    public void start() throws Exception {
        if (this.liveStream.isActive()) {
            throw new Exception("can't encode chat while livestream is active");
        }

        if (!this.preChatCombinedFile.exists()) {
            throw new Exception("pre-chat combined file doesn't exist");
        }

        int outputFps = getFileFps(this.preChatCombinedFile);

        System.out.println("outputFps=" + outputFps);

        this.liveStream.lockSegments();

        try {
            long perFrame = 1000L / FRAMES_PER_SECOND;
            int width = (int) scale(IMAGE_WIDTH);
            int height = (int) scale(IMAGE_HEIGHT);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();

            Map<?, ?> desktopHints =
                    (Map<?, ?>) Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");

            if (desktopHints != null) {
                System.out.println("setting hints");
                graphics.setRenderingHints(desktopHints);
            }

            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

            graphics.setFont(new Font("Arial", Font.PLAIN, (int) scale(13)));
            graphics.setBackground(new Color(0.25f, 0.25f, 0.25f, 0.5f));
            graphics.clearRect(0, 0, width, height);

            FontMetrics fontMetrics = graphics.getFontMetrics();

            Process process;

            File outFile = new File(new File(liveStream.getFileDirectory()).getParentFile()
                    , liveStream.getLiveStreamId() + "_chat.mp4");

            outFile.getParentFile().mkdirs();

            try {
//                , "-vcodec", "mpeg4"
                process = Runtime.getRuntime().exec(new String[]{"ffmpeg", "-y", "-f", "image2pipe", "-framerate", String.valueOf(FRAMES_PER_SECOND), "-i", "-", "-vcodec", "libx264", "-r", String.valueOf(outputFps), outFile.getAbsolutePath()});
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // The process output stream hangs if you don't read from the error stream. no idea why
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    while (reader.readLine() != null) {
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            try (BufferedOutputStream out = new BufferedOutputStream(process.getOutputStream())) {
                long totalLength = 0L;
                Segment lastSegment = null;

                // TODO This will break if segments are out of order
                for (Segment segment : this.liveStream.getSegments()) {
                    if (lastSegment == null && segment.getIndex() != 0) {
                        totalLength += (segment.getLength() * 1000L) * segment.getIndex();
                    } else if (lastSegment != null && segment.getIndex() != lastSegment.getIndex() + 1) {
                        totalLength += (segment.getIndex() - lastSegment.getIndex()) * (lastSegment.getLength() * 1000L);
                    }

                    totalLength += segment.getLength() * 1000L;
                    lastSegment = segment;
                }

                List<ChatMessage> pastMessages = new ArrayList<>();

                Segment indexSegment = this.liveStream.getSegments().get(0);
                int indexFrame = (int) (indexSegment.getLength() * indexSegment.getIndex()) * FRAMES_PER_SECOND;

                System.out.println("indexFrame=" + indexFrame);
                System.out.println("totalFrames=" + (FRAMES_PER_SECOND * (totalLength / 1000L)));

                for (int frame = indexFrame; frame < FRAMES_PER_SECOND * (totalLength / 1000L); frame++) {
                    if (frame == 61410) {
                        int i = 0;
                    }

                    System.out.println(frame + " - " + (double) frame / (FRAMES_PER_SECOND * (totalLength / 1000L)));
                    long frameStart = this.liveStream.getStartTime() + (frame * perFrame) + 75000L; // 75 seconds to counteract the delay
                    long frameEnd = frameStart + perFrame;

                    List<ChatMessage> chatMessages = liveStream.getChatIngest()
                            .getMessagesInRange(frameStart, frameEnd);

                    if (chatMessages.size() == 0) {
                        ImageIO.write(image, "png", out);
                        continue;
                    }

                    System.out.println("chatMessages.size=" + chatMessages.size());

                    graphics.clearRect(0, 0, width, height);

                    chatMessages.sort(Comparator.comparingInt(a -> (int) a.getTimestamp()));
                    pastMessages.addAll(chatMessages);

                    int messagePadding = 8;
                    int heightLeft = height - 5;

                    for (int i = pastMessages.size() - 1; i >= 0; i--) {
                        ChatMessage chatMessage = pastMessages.get(i);

                        if (heightLeft <= 0) {
                            pastMessages.remove(i);
                            continue;
                        }

                        int msgWidth = width - (int) scale(20);
                        int msgHeight = chatMessage.getPaintHeight(imageCache, fontMetrics, msgWidth);

                        chatMessage.paint(imageCache, fontMetrics, (int) scale(10), heightLeft - msgHeight, msgWidth, graphics);
                        heightLeft -= msgHeight + messagePadding;
                    }

//                    JFrame frameX = new JFrame();
//                    frameX.getContentPane().setLayout(new FlowLayout());
//                    frameX.getContentPane().add(new JLabel(new ImageIcon(image)));
//                    frameX.pack();
//                    frameX.setVisible(true);
                    ImageIO.write(image, "png", out);
//                    ImageIO.write(image, "png", new File("frame-" + frame + ".png"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

//            process.waitFor();
//            System.out.println("process done");


        } finally {
            this.liveStream.releaseSegments();
        }
    }

    private int getFileFps(File file) throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{"ffmpeg", "-i", file.getAbsolutePath()});

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            StringBuilder fullTextBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                fullTextBuilder.append(line);
            }

            String fullText = fullTextBuilder.toString();
            String firstSec = fullText.split(" fps")[0];

            return Integer.parseInt(firstSec.substring(firstSec.length() - 2, firstSec.length()));
        }
    }

    private static <T extends Number> float scale(T in) {
        return in.floatValue() * SCALE;
    }
}
