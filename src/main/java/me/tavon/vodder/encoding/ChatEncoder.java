package me.tavon.vodder.encoding;

import com.google.common.base.Joiner;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.Segment;
import me.tavon.vodder.stream.chat.ChatMessage;
import okhttp3.OkHttpClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatEncoder {

//    ffmpeg -i dcfyX9q7Yc8.ts -i dcfyX9q7Yc8_chat.mp4 -filter_complex "scale=w=1560:h=-1[sc];[sc]pad=width=1560:height=1080:y=(oh-ih)/2[sc];[sc][1]hstack" -c:v libx264 -crf 18 hello_9.ts

    private LiveStream liveStream;
    private File preChatCombinedFile;
    private ImageCache imageCache;

    private static final float SCALE = 1f;
    private static final int IMAGE_WIDTH = 360;
    private static final int IMAGE_HEIGHT = 1080;
    private static final int FRAMES_PER_SECOND = 10; // Must be a factor of 1000

    private static final int X_CHAT_FPS = 1;

    public ChatEncoder(LiveStream liveStream, File preChatCombinedFile, OkHttpClient httpClient) {
        this.liveStream = liveStream;
        this.preChatCombinedFile = preChatCombinedFile;
        this.imageCache = new ImageCache(httpClient);
    }

    public void xstart() throws Exception {
        if (this.liveStream.isActive()) {
            throw new RuntimeException("can't encode chat while livestream is active");
        }

        if (!this.preChatCombinedFile.exists()) {
            throw new RuntimeException("pre-chat combined file doesn't exist");
        }

        this.liveStream.lockSegments();

        Map.Entry<Integer, Integer> dimensions = this.getFileDimensions(this.preChatCombinedFile);
        int outputFps = getFileFps(this.preChatCombinedFile);
        System.out.println("outputFPS = " + outputFps);
        int width = (int) Math.round(((double) IMAGE_WIDTH / (double) IMAGE_HEIGHT) * (double) dimensions.getValue());
        int height = dimensions.getValue();
        System.out.println("width=" + width + " height=" + height);

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
        graphics.setFont(new Font("Arial", Font.PLAIN, width / 15));
        graphics.setBackground(new Color(0.25f, 0.25f, 0.25f, 0.5f));
        graphics.clearRect(0, 0, width, height);

        FontMetrics fontMetrics = graphics.getFontMetrics();

        File outFile = new File(new File(liveStream.getFileDirectory()).getParentFile()
                , liveStream.getLiveStreamId() + "_chat.mp4");

        outFile.getParentFile().mkdirs();

        Process process;

        try {
//                , "-vcodec", "mpeg4"
            process = Runtime.getRuntime().exec("ffmpeg -y -f image2pipe -framerate " +
                    String.valueOf(X_CHAT_FPS) + " -i - -c:v libx264 -r " + String.valueOf(1) + " " +
                    outFile.getAbsolutePath());
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

        double streamLength = 0;

        for (Segment segment : this.liveStream.getSegments()) {
            if (!segment.isDownloaded()) {
                continue;
            }

            streamLength += segment.getLength();
        }

        System.out.println("streamLength = " + streamLength);

        List<Map.Entry<Segment, Map.Entry<Double, Double>>> segmentBreaks = this.liveStream.getSegmentBreaks();
        Map.Entry<Segment, Map.Entry<Double, Double>> indexSeg = segmentBreaks.get(0);

        for (Map.Entry<Segment, Map.Entry<Double, Double>> entry : segmentBreaks) {
            if (entry.getKey().isDownloaded()) {
                indexSeg = entry;
                break;
            }
        }

        double frameTime = indexSeg.getValue().getKey();
        int breakIndex = 0;
        List<ChatMessage> pastMessages = new ArrayList<>();

        try (BufferedOutputStream out = new BufferedOutputStream(process.getOutputStream())) {
            for (int i = 0; i < X_CHAT_FPS * (Math.round(streamLength)); i++) {
                List<Map.Entry<Segment, Map.Entry<Double, Double>>> displaySegs = new LinkedList<>();

                while (breakIndex < segmentBreaks.size()) {
                    if (!segmentBreaks.get(breakIndex).getKey().isDownloaded()) {
                        breakIndex++;
                        continue;
                    }

                    if (frameTime < segmentBreaks.get(breakIndex).getValue().getKey() || frameTime >= segmentBreaks.get(breakIndex).getValue().getValue()) {
                        break;
                    }

                    displaySegs.add(new AbstractMap.SimpleEntry<>(segmentBreaks.get(breakIndex)));
                    breakIndex++;
                }

                int count = 0;

                for (Map.Entry<Segment, Map.Entry<Double, Double>> entry : displaySegs) {
                    List<ChatMessage> chatMessages = liveStream.getChatIngest()
                            .getMessagesInRange(Math.round(entry.getValue().getKey() * 1000D),
                                    Math.round(entry.getValue().getValue() * 1000D));

                    count += chatMessages.size();
                    pastMessages.addAll(chatMessages);
                }

                if (count == 0) {
                    ImageIO.write(image, "png", out);
                    System.out.println((double) i / (X_CHAT_FPS * (Math.round(streamLength))));
                    frameTime += 1D / X_CHAT_FPS;
                    continue;
                }

                pastMessages.sort(Comparator.comparingInt(a -> (int) a.getTimestamp()));
                graphics.clearRect(0, 0, width, height);

                int messagePadding = width / 45;
                int heightLeft = height - (width / 72);

                for (int x = pastMessages.size() - 1; x >= 0; x--) {
                    ChatMessage chatMessage = pastMessages.get(x);

                    if (heightLeft <= 0) {
                        pastMessages.remove(x);
                        continue;
                    }

                    int msgWidth = width - (width / 18);
                    int msgHeight = chatMessage.getPaintHeight(imageCache, fontMetrics, msgWidth);

                    chatMessage.paint(imageCache, fontMetrics, width / 36, heightLeft - msgHeight, msgWidth, graphics);
                    heightLeft -= msgHeight + messagePadding;
                }

                ImageIO.write(image, "png", out);
                System.out.println((double) i / (X_CHAT_FPS * (Math.round(streamLength))) + " (data)");

                frameTime += 1D / X_CHAT_FPS;
            }
        }

        Thread.sleep(3000);

        combineVodChat(outFile);
    }

    private void combineVodChat(File chatFile) throws IOException {
        Map.Entry<Integer, Integer> vodDim = this.getFileDimensions(this.preChatCombinedFile);
        Map.Entry<Integer, Integer> chatDim = this.getFileDimensions(chatFile);
        Process process;

        System.out.println("ffmpeg -y -i " + this.preChatCombinedFile.getAbsolutePath() + " -i "
                + chatFile.getAbsolutePath() + " -filter_complex scale=w=" + (vodDim.getKey() - chatDim.getKey()) + ":h=-1[sc];[sc]" +
                "pad=width=" + (vodDim.getKey() - chatDim.getKey()) + ":height=" + vodDim.getValue() +
                ":y=(oh-ih)/2[sc];[sc][1]hstack -c:v libx264 -crf 18 "
                + this.liveStream.getLiveStreamId() + "_combined.ts");

        process = Runtime.getRuntime().exec("ffmpeg -y -i " + this.preChatCombinedFile.getAbsolutePath() + " -i "
                + chatFile + " -filter_complex scale=w=" + (vodDim.getKey() - chatDim.getKey()) + ":h=-1[sc];[sc]" +
                "pad=width=" + (vodDim.getKey() - chatDim.getKey()) + ":height=" + vodDim.getValue() +
                ":y=(oh-ih)/2[sc];[sc][1]hstack -pix_fmt yuv420p -preset fast -c:v libx264 -crf 18 -c:a aac -movflags +faststart "
                + new File(chatFile.getParentFile(), this.liveStream.getLiveStreamId() + "_combined.mp4"));



        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String s;
            while ((s = reader.readLine()) != null) {
                System.out.println(s);
            }
        }
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

    private Map.Entry<Integer, Integer> getFileDimensions(File file) throws IOException {
        System.out.println("getFileDimensions " + file.getAbsolutePath());
        Process process = Runtime.getRuntime().exec("ffmpeg -i " + file.getAbsolutePath());
        Pattern pattern = Pattern.compile(" (\\b[^0]\\d+x[^0]\\d+\\b)");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);

                if (!matcher.find()) {
                    continue;
                }

                line = matcher.group().trim();

                if (line.contains("x")) {
                    return new AbstractMap.SimpleEntry<>(Integer.parseInt(line.split("x")[0]),
                            Integer.parseInt(line.split("x")[1]));
                }
            }

            throw new RuntimeException("could not find file dimensions");
        }
    }

    private static <T extends Number> float scale(T in) {
        return in.floatValue() * SCALE;
    }
}
