package me.tavon.vodder.channel;

public enum Platform {

    YOUTUBE("YouTube", "https://www.youtube.com/channel/%s"),
    TWITCH("Twitch", "https://www.twitch.tv/%s"),
    LIVE_ME("live.me", "http://www.liveme.com/personal.html?userId=%s");

    private String displayName;
    private String urlFormat;

    Platform(String displayName, String urlFormat) {
        this.displayName = displayName;
        this.urlFormat = urlFormat;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUrl(Channel channel) throws Exception {
        switch (this) {
            case LIVE_ME:
            case YOUTUBE:
                return String.format(this.urlFormat, channel.getChannelId());
            case TWITCH:
                return String.format(this.urlFormat, channel.getPlatformDriver()
                        .getChannelDisplayName(channel.getChannelId()));
            default:
                throw new Exception("unknown platform " + this.name());
        }
    }
}
