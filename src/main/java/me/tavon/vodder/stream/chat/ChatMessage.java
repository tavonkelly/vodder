package me.tavon.vodder.stream.chat;

import me.tavon.vodder.encoding.ImageCache;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatMessage implements Serializable {

    private static final long serialVersionUID = -2958535797629417747L;

    private String id;
    private long timestamp;
    private String sender;
    private List<String> badges;
    private List<ChatRun> messageRuns;

    public ChatMessage(String id, long timestamp, String sender, List<String> badges, List<ChatRun> messageRuns) {
        this.id = id;
        this.timestamp = timestamp;
        this.sender = sender;
        this.badges = badges;
        this.messageRuns = messageRuns;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSender() {
        return sender;
    }

    public List<String> getBadges() {
        return badges;
    }

    public List<ChatRun> getMessageRuns() {
        return messageRuns;
    }

    public int getPaintHeight(ImageCache imageCache, FontMetrics fontMetrics, int width) {
        return wrapData(messageRunsToArr(imageCache), fontMetrics, width).size() * fontMetrics.getHeight();
    }

    // TODO https://stackoverflow.com/questions/22240328/how-to-draw-a-gif-animation-in-java/22240487#
    // TODO https://www.mail-archive.com/batik-users@xml.apache.org/msg00775.html
    public void paint(ImageCache imageCache, FontMetrics fontMetrics, int xOrigin, int yOrigin, int width, Graphics2D graphics2D) {
        graphics2D.setColor(Color.WHITE);
        List<Object[]> lines = wrapData(messageRunsToArr(imageCache), fontMetrics, width);
        int newOrigin;

//        graphics2D.

        for (int i = 0; i < lines.size(); i++) {
            char buffer[] = new char[lines.get(i).length];
            int bufferLen = 0;
            Color lastColor = null;
            newOrigin = xOrigin;

            boolean dumpBuffer = false;

            for (int x = 0; x < lines.get(i).length; x++) {
                if (dumpBuffer) {
                    String s = new String(buffer, 0, bufferLen);
                    Color oldColor = graphics2D.getColor();

                    if (lastColor != null) {
                        graphics2D.setColor(lastColor);
                        lastColor = null;
                    }

                    graphics2D.drawString(s, newOrigin, yOrigin + ((i + 1) * fontMetrics.getHeight()));
                    graphics2D.setColor(oldColor);
                    newOrigin += fontMetrics.stringWidth(s);
                    bufferLen = 0;
                }

                Object o = lines.get(i)[x];

                if (o instanceof ColoredCharacter) {
                    ColoredCharacter c = (ColoredCharacter) o;

                    if (lastColor != null && lastColor != c.color) {
                        dumpBuffer = true;
                        x--;
                        continue;
                    }

                    buffer[bufferLen++] = c.character;
                    lastColor = c.color;
                } else if (o instanceof BufferedImage) {
                    if (bufferLen != 0) {
                        dumpBuffer = true;
                        x--;
                        continue;
                    }

                    graphics2D.drawImage((BufferedImage) o, null, newOrigin, yOrigin + (i * fontMetrics.getHeight()));
                    newOrigin += ((BufferedImage) o).getWidth();

                }
            }

            if (bufferLen != 0) {
                String s = new String(buffer, 0, bufferLen);
                Color oldColor = graphics2D.getColor();

                if (lastColor != null) {
                    graphics2D.setColor(lastColor);
                }
                graphics2D.drawString(s, newOrigin, yOrigin + ((i + 1) * fontMetrics.getHeight()));
                graphics2D.setColor(oldColor);
            }
        }
    }

    private Object[] messageRunsToArr(ImageCache imageCache) {
        List<Object> objects = new ArrayList<>();

        Color color = colorFromString(this.sender);

        for (char c : this.sender.toCharArray()) {
            objects.add(new ColoredCharacter(color, c));
        }

        objects.addAll(Arrays.asList(new ColoredCharacter(Color.WHITE, ':'),
                new ColoredCharacter(Color.WHITE, ' ')));

        for (ChatRun chatRun : this.messageRuns) {
            if (chatRun.type == ChatRunType.TEXT) {
                for (char c : chatRun.data.toCharArray()) {
                    if (c == '\uFEFF') {
                        continue;
                    }

                    objects.add(new ColoredCharacter(Color.WHITE, c));
                }
            } else if (chatRun.type == ChatRunType.ICON) {
                try {
                    objects.add(imageCache.getImage(chatRun.data));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return objects.toArray(new Object[objects.size()]);
    }

    private Color colorFromString(String s) {
        double hashU;
        double hashV;

        hashU = s.hashCode() + ((double) Integer.MIN_VALUE * -1D);

        char[] chars = s.toCharArray();

        for (char c = 0; c < chars.length / 2; c++) {
            char x = chars[c];
            chars[c] = chars[chars.length - 1 - c];
            chars[chars.length - 1 - c] = x;
        }

        hashV = new String(chars).hashCode() + ((double) Integer.MIN_VALUE * -1D);

        double randoU = hashU / (Integer.MAX_VALUE + ((double) Integer.MIN_VALUE * -1D));
        double randoV = hashV / (Integer.MAX_VALUE + ((double) Integer.MIN_VALUE * -1D));
        double y = 0.75D;
        double u = randoU - 0.5D;
        double v = randoV - 0.5D;

        double rTmp = y + (1.403 * v);
        double gTmp = y - (0.344 * u) - (0.714 * v);
        double bTmp = y + (1.770 * u);

        int r = (int) Math.round(Math.min(255, Math.max(0, rTmp * 255)));
        int g = (int) Math.round(Math.min(255, Math.max(0, gTmp * 255)));
        int b = (int) Math.round(Math.min(255, Math.max(0, bTmp * 255)));

        return new Color(r, g, b);
    }

    private List<Object[]> wrapData(Object[] data, FontMetrics fontMetrics, int width) {
        List<Object[]> lines = new ArrayList<>();
        Object[] buffer = new Object[data.length];
        int bufferLen = 0;

        for (int i = 0; i < data.length; i++) {
            buffer[bufferLen++] = data[i];

            if (getObjectArrLen(fontMetrics, Arrays.copyOfRange(buffer, 0, bufferLen)) > width) {
                bufferLen--;
                lines.add(Arrays.copyOfRange(buffer, 0, bufferLen));

                bufferLen = 0;
                buffer[bufferLen++] = data[i];
            }
        }

        if (bufferLen != 0) {
            lines.add(Arrays.copyOfRange(buffer, 0, bufferLen));
        }

        return lines;
    }

    private int getObjectArrLen(FontMetrics fontMetrics, Object[] input) {
        int length = 0;

        for (Object anInput : input) {
            if (anInput instanceof ColoredCharacter) {
                length += fontMetrics.charWidth(((ColoredCharacter) anInput).character);
            } else if (anInput instanceof BufferedImage) {
                length += ((BufferedImage) anInput).getWidth();
            }
        }

        return length;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", timestamp=" + timestamp +
                ", sender='" + sender + '\'' +
                ", badges=" + badges +
                ", messageRuns=" + messageRuns +
                '}';
    }

    public static class ChatRun implements Serializable {

        private static final long serialVersionUID = 817861820557168337L;

        private ChatRunType type;
        private String data;

        public ChatRun(ChatRunType type, String data) {
            this.type = type;
            this.data = data;
        }

        public ChatRunType getType() {
            return type;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    public enum ChatRunType {
        ICON,
        TEXT,
    }

    private class ColoredCharacter {
        private Color color;
        private Character character;

        public ColoredCharacter(Color color, Character character) {
            this.color = color;
            this.character = character;
        }
    }
}
