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
        return wrapData(messageRunsToArr(this.messageRuns, imageCache), fontMetrics, width).size() * fontMetrics.getHeight();
    }

    // TODO https://stackoverflow.com/questions/22240328/how-to-draw-a-gif-animation-in-java/22240487#
    // TODO https://www.mail-archive.com/batik-users@xml.apache.org/msg00775.html
    public void paint(ImageCache imageCache, FontMetrics fontMetrics, int xOrigin, int yOrigin, int width, Graphics2D graphics2D) {
        graphics2D.setColor(Color.WHITE);
        List<Object[]> lines = wrapData(messageRunsToArr(this.messageRuns, imageCache), fontMetrics, width);
        int newOrigin;

//        graphics2D.

        for (int i = 0; i < lines.size(); i++) {
            char buffer[] = new char[lines.get(i).length];
            int bufferLen = 0;
            newOrigin = xOrigin;

            for (Object o : lines.get(i)) {
                if (o instanceof Character) {
                    buffer[bufferLen++] = (Character) o;
                } else if (o instanceof BufferedImage) {
                    if (bufferLen != 0) {
                        String s = new String(buffer, 0, bufferLen);
                        graphics2D.drawString(s, newOrigin, yOrigin + ((i + 1) * fontMetrics.getHeight()));
                        newOrigin += fontMetrics.stringWidth(s);
                        bufferLen = 0;
                    }

                    graphics2D.drawImage((BufferedImage) o, null, newOrigin, yOrigin + (i * fontMetrics.getHeight()));
                    newOrigin += ((BufferedImage) o).getWidth();

                }
            }

            if (bufferLen != 0) {
                String s = new String(buffer, 0, bufferLen);
                graphics2D.drawString(s, newOrigin, yOrigin + ((i + 1) * fontMetrics.getHeight()));
            }
        }
    }

    private Object[] messageRunsToArr(List<ChatRun> chatRuns, ImageCache imageCache) {
        List<Object> objects = new ArrayList<>();

        for (char c : this.sender.toCharArray()) {
            objects.add(c);
        }

        objects.addAll(Arrays.asList(':', ' '));

        for (ChatRun chatRun : chatRuns) {
            if (chatRun.type == ChatRunType.TEXT) {
                for (char c : chatRun.data.toCharArray()) {
                    if (c == '\uFEFF') {
                        continue;
                    }

                    objects.add(c);
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
            if (anInput instanceof Character) {
                length += fontMetrics.charWidth((Character) anInput);
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

        @Override
        public String toString() {
            return "ChatRun{" +
                    "type=" + type +
                    ", data='" + toPrintableRepresentation(data) + '\'' +
                    '}';
        }
    }

    public enum ChatRunType {
        ICON,
        TEXT,
    }

    private static final char CONTROL_LIMIT = ' ';
    private static final char PRINTABLE_LIMIT = '\u007e';
    private static final char[] HEX_DIGITS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String toPrintableRepresentation(String source) {

        if (source == null) return null;
        else {

            final StringBuilder sb = new StringBuilder();
            final int limit = source.length();
            char[] hexbuf = null;

            int pointer = 0;

            sb.append('"');

            while (pointer < limit) {

                int ch = source.charAt(pointer++);

                switch (ch) {

                    case '\0':
                        sb.append("\\0");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;

                    default:
                        if (CONTROL_LIMIT <= ch && ch <= PRINTABLE_LIMIT) sb.append((char) ch);
                        else {

                            sb.append("\\u");

                            if (hexbuf == null)
                                hexbuf = new char[4];

                            for (int offs = 4; offs > 0; ) {

                                hexbuf[--offs] = HEX_DIGITS[ch & 0xf];
                                ch >>>= 4;
                            }

                            sb.append(hexbuf, 0, 4);
                        }
                }
            }

            return sb.append('"').toString();
        }
    }
}
