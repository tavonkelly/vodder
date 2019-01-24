package me.tavon.vodder.driver.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.tavon.vodder.driver.chat.emote.EmoteRegistry;
import me.tavon.vodder.stream.LiveStream;
import me.tavon.vodder.stream.chat.ChatIngest;
import me.tavon.vodder.stream.chat.ChatMessage;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class YoutubeChatDriver extends PlatformChatDriver {

    private OkHttpClient client;
    private Map<String, String> continuationMap = new ConcurrentHashMap<>();
    private Map<String, String> chatApiKeyMap = new ConcurrentHashMap<>();
    private Map<String, ChatIngest> ingestMap = new ConcurrentHashMap<>();
    private Map<String, String> emojiMap = new HashMap<>();

    private static ExecutorService chatDownloadThread = Executors.newFixedThreadPool(1);

    public YoutubeChatDriver(OkHttpClient client, List<EmoteRegistry> emoteRegistries) {
        super(emoteRegistries);
        this.client = client;

        new Timer("YoutubeChatDriver").schedule(new TimerTask() {
            @Override
            public void run() {
                for (Map.Entry<String, String> entry : continuationMap.entrySet()) {
                    chatDownloadThread.submit(() -> {
                        try {
                            requestChatUpdate(entry.getKey(), entry.getValue()
                                    , chatApiKeyMap.get(entry.getKey()), ingestMap.get(entry.getKey()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }, TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(5));
    }

    @Override
    public void onLiveStreamIngestStart(LiveStream liveStream, ChatIngest chatIngest) throws Exception {
        Request request = new Request.Builder()
                .url("https://gaming.youtube.com/live_chat?is_popout=1&v=" + liveStream.getLiveStreamId())
                .get()
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.186 Safari/537.36")
                .addHeader("Cache-Control", "no-cache")
                .build();

        Response response = null;

        try {
            response = client.newCall(request).execute();

            ResponseBody body = response.body();

            if (response.code() != 200 || body == null) {
                throw new Exception("Didn't get 200 or empty body");
            }

            String bodyString = body.string();
            JsonObject initialData = new JsonParser().parse(bodyString.split(
                    Pattern.quote("window[\"ytInitialData\"] = "))[1].split(Pattern.quote("};"))[0] + "}")
                    .getAsJsonObject();

            String continuation = initialData.getAsJsonObject("contents").getAsJsonObject("liveChatRenderer")
                    .getAsJsonArray("continuations").get(0).getAsJsonObject()
                    .getAsJsonObject("timedContinuationData").get("continuation").getAsString();
            String chatApiKey = bodyString.split(Pattern.quote("INNERTUBE_API_KEY\":\""))[1]
                    .split(Pattern.quote("\""))[0];

            continuationMap.put(liveStream.getLiveStreamId(), continuation);
            chatApiKeyMap.put(liveStream.getLiveStreamId(), chatApiKey);
            ingestMap.put(liveStream.getLiveStreamId(), chatIngest);

            if (!initialData.has("contents")) {
                return;
            }

            JsonArray emojis = initialData.getAsJsonObject("contents").getAsJsonObject("liveChatRenderer")
                    .getAsJsonArray("emojis");

            for (int x = 0; x < emojis.size(); x++) {
                JsonObject emojiObj = emojis.get(x).getAsJsonObject();
                emojiMap.put(emojiObj.get("emojiId").getAsString(), emojiObj.getAsJsonObject("image")
                        .getAsJsonArray("thumbnails").get(0).getAsJsonObject().get("url").getAsString());
            }

            JsonArray actions = initialData.getAsJsonObject("contents").getAsJsonObject("liveChatRenderer")
                    .getAsJsonArray("actions");

            chatDownloadThread.submit(() -> handleActions(actions, chatIngest));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private void requestChatUpdate(String livestreamId, String continuation, String chatApiKey, ChatIngest chatIngest) throws Exception {
        // TODO We out here widlin
        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/octet-stream");
        RequestBody body = RequestBody.create(mediaType, "{\"context\":{\"client\":{\"clientName\":\"WEB_GAMI" +
                "NG\",\"clientVersion\":\"1.92\",\"hl\":\"en\",\"gl\":\"US\",\"experimentIds\":[],\"theme\":\"GAMI" +
                "NG\"},\"capabilities\":{},\"request\":{\"internalExperimentFlags\":[{\"key\":\"use_push_for_deskt" +
                "op_live_chat\",\"value\":\"true\"},{\"key\":\"live_chat_viewer_blocks_enable_filtering\",\"valu" +
                "e\":\"true\"},{\"key\":\"polymer_page_data_load_refactoring\",\"value\":\"true\"},{\"key\":\"live_" +
                "chat_unhide_on_channel\",\"value\":\"true\"},{\"key\":\"enable_youtubei_innertube\",\"value\":\"" +
                "true\"},{\"key\":\"interaction_click_on_gel_web\",\"value\":\"true\"},{\"key\":\"channel_about_page" +
                "_gadgets\",\"value\":\"true\"},{\"key\":\"live_chat_read_badge_on_event\",\"value\":\"true\"},{\"k" +
                "ey\":\"enable_gaming_comments_sponsor_badge\",\"value\":\"true\"},{\"key\":\"optimistically_cr" +
                "eate_transport_client\",\"value\":\"true\"},{\"key\":\"youtubei_for_web\",\"value\":\"true\"}," +
                "{\"key\":\"live_chat_viewer_blocks_enable_cache_filling\",\"value\":\"true\"},{\"key\":\"live_" +
                "chat_inline_moderation\",\"value\":\"true\"},{\"key\":\"live_chat_use_new_default_filter_mode\"," +
                "\"value\":\"true\"},{\"key\":\"polymer_live_chat\",\"value\":\"true\"},{\"key\":\"interaction_" +
                "logging_on_gel_web\",\"value\":\"true\"},{\"key\":\"spaces_desktop\",\"value\":\"true\"},{\"" +
                "key\":\"live_chat_flagging_reasons\",\"value\":\"true\"},{\"key\":\"live_chat_replay\",\"va" +
                "lue\":\"true\"},{\"key\":\"custom_emoji_legacy\",\"value\":\"true\"},{\"key\":\"third_part" +
                "y_integration\",\"value\":\"true\"},{\"key\":\"custom_emoji_main_app\",\"value\":\"true\"}" +
                ",{\"key\":\"log_window_onerror_fraction\",\"value\":\"1\"},{\"key\":\"live_chat_viewer_blo" +
                "cks_show_ui\",\"value\":\"true\"},{\"key\":\"custom_emoji_super_chat\",\"value\":\"true\"},{\"" +
                "key\":\"enable_gaming_new_logo\",\"value\":\"true\"},{\"key\":\"chat_smoothing_animations\"" +
                ",\"value\":\"84\"},{\"key\":\"live_chat_replay_milliqps_threshold\",\"value\":\"5000\"},{\"key\"" +
                ":\"html5_serverside_pagead_id_sets_cookie\",\"value\":\"true\"},{\"key\":\"live_chat_top_chat_w" +
                "indow_length_sec\",\"value\":\"4\"},{\"key\":\"custom_emoji_creator\",\"value\":\"true\"},{\"ke" +
                "y\":\"remove_web_visibility_batching\",\"value\":\"true\"},{\"key\":\"log_foreground_heartbeat" +
                "_gaming\",\"value\":\"true\"},{\"key\":\"live_chat_message_sampling_rate\",\"value\":\"3\"},{\"ke" +
                "y\":\"live_fresca_v2\",\"value\":\"true\"},{\"key\":\"log_js_exceptions_fraction\",\"value\":\"" +
                "1\"},{\"key\":\"very_optimistically_create_gel_client\",\"value\":\"true\"},{\"key\":\"lact_loc" +
                "al_listeners\",\"value\":\"true\"},{\"key\":\"debug_forced_promo_id\",\"value\":\"\"},{\"key\":\"" +
                "live_chat_replay_viewer_disclosure\",\"value\":\"true\"},{\"key\":\"live_chat_top_chat_spli" +
                "t\",\"value\":\"0.5\"},{\"key\":\"custom_emoji_desktop\",\"value\":\"true\"}]}},\"continuatio" +
                "n\":\"" + continuation + "\",\"isInvalidationTimeoutRequest\":\"true\"}");
        Request request = new Request.Builder()
                .url("https://gaming.youtube.com/youtubei/v1/live_chat/get_live_chat?key=" + chatApiKey)
                .post(body)
                .addHeader("Cache-Control", "no-cache")
                .build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();

            if (response.code() != 200 || responseBody == null) {
                throw new Exception("Didn't get 200 or empty body");
            }

            JsonObject data = new JsonParser().parse(responseBody.string()).getAsJsonObject()
                    .getAsJsonObject("continuationContents").getAsJsonObject("liveChatContinuation");

            JsonObject continuationContainer = data.getAsJsonArray("continuations").get(0).getAsJsonObject();
            String newContinuation;

            if (continuationContainer.has("timedContinuationData")) {
                newContinuation = continuationContainer.getAsJsonObject("timedContinuationData")
                        .get("continuation").getAsString();
            } else {
                newContinuation = continuationContainer.getAsJsonObject("invalidationContinuationData")
                        .get("continuation").getAsString();
            }

            continuationMap.put(livestreamId, newContinuation);

            if (data.has("actions")) {
                handleActions(data.getAsJsonArray("actions"), chatIngest);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private void handleActions(JsonArray actions, ChatIngest chatIngest) {
        Map<String, String> allEmotes = new HashMap<>();
        allEmotes.putAll(emojiMap);

        for (EmoteRegistry emoteRegistry : emoteRegistries) {
            allEmotes.putAll(emoteRegistry.getEmoteMap());
        }

        for (int i = 0; i < actions.size(); i++) {
            JsonObject jsonObject = actions.get(i).getAsJsonObject();

            if (!jsonObject.has("addChatItemAction")) {
                continue;
            }

            if (!jsonObject.getAsJsonObject("addChatItemAction").getAsJsonObject("item")
                    .has("liveChatTextMessageRenderer")) {
                continue;
            }

            JsonObject chatObject = jsonObject.getAsJsonObject("addChatItemAction").getAsJsonObject("item")
                    .getAsJsonObject("liveChatTextMessageRenderer");

            String id = chatObject.get("id").getAsString();

            if (chatIngest.getChatMessages().containsKey(id)) {
                continue;
            }

            long timestamp = Long.valueOf(chatObject.get("timestampUsec").getAsString()) / 1000L;
            String author = chatObject.getAsJsonObject("authorName").getAsJsonArray("runs")
                    .get(0).getAsJsonObject().get("text").getAsString();

            List<String> badges = new LinkedList<>();

            if (chatObject.has("authorBadges")) {
                JsonArray jsonBadges = chatObject.getAsJsonArray("authorBadges");

                for (int x = 0; x < jsonBadges.size(); x++) {
                    JsonObject object = jsonBadges.get(x).getAsJsonObject()
                            .getAsJsonObject("liveChatAuthorBadgeRenderer");

                    if (!object.has("customThumbnail")) { // TODO Implement default youtube badges
                        continue;
                    }

                    JsonArray jsonThumbs = object.getAsJsonObject("customThumbnail")
                            .getAsJsonArray("thumbnails");

                    for (int y = 0; y < jsonThumbs.size(); y++) {
                        String url = jsonThumbs.get(y).getAsJsonObject().get("url").getAsString();

                        badges.add(url);
                    }
                }
            }

            List<ChatMessage.ChatRun> chatRuns = new LinkedList<>();

            JsonArray jsonRuns = chatObject.getAsJsonObject("message").getAsJsonArray("runs");

            for (int x = 0; x < jsonRuns.size(); x++) {
                JsonObject runObj = jsonRuns.get(x).getAsJsonObject();

                if (runObj.has("text")) {
                    chatRuns.add(new ChatMessage.ChatRun(ChatMessage.ChatRunType.TEXT
                            , runObj.get("text").getAsString()));
                    continue;
                }

                if (runObj.has("emoji")) {
                    String emojiUrl = runObj.getAsJsonObject("emoji").getAsJsonObject("image")
                            .getAsJsonArray("thumbnails").get(0)
                            .getAsJsonObject().get("url").getAsString();

                    chatRuns.add(new ChatMessage.ChatRun(ChatMessage.ChatRunType.ICON, emojiUrl));
                }
            }

            for (int x = 0; x < chatRuns.size(); x++) {
                ChatMessage.ChatRun chatRun = chatRuns.get(x);

                if (chatRun.getType() == ChatMessage.ChatRunType.ICON) {
                    continue;
                }

                chatRun.setData(chatRun.getData().replace("\ufeff", ""));

                for (Map.Entry<String, String> entry : allEmotes.entrySet()) {
                    chatRun = chatRuns.get(x);
                    if (!chatRun.getData().contains(entry.getKey())) {
                        continue;
                    }

                    chatRuns.remove(x);

                    String data = chatRun.getData();
                    int index = data.indexOf(entry.getKey());
                    int listIndex = 0;

                    while (index != -1) {
                        String beginning = data.substring(0, index);
                        String middle = entry.getValue();

                        if (!beginning.isEmpty()) {
                            chatRuns.add(x + listIndex++
                                    , new ChatMessage.ChatRun(ChatMessage.ChatRunType.TEXT, beginning));
                        }

                        chatRuns.add(x + listIndex++
                                , new ChatMessage.ChatRun(ChatMessage.ChatRunType.ICON, middle));

                        data = data.substring(index + entry.getKey().length(), data.length());
                        index = data.indexOf(entry.getKey());
                    }

                    if (!data.isEmpty()) {
                        chatRuns.add(x + listIndex
                                , new ChatMessage.ChatRun(ChatMessage.ChatRunType.TEXT, data));
                    }

//                    int index = chatRun.getData().indexOf(entry.getKey());
//                    String beginning = chatRun.getData().substring(0, index);
//                    String middle = entry.getValue();
//                    String end = chatRun.getData().substring(index + entry.getKey().length()
//                            , chatRun.getData().length());
//
//                    if (!end.isEmpty()) {
//                        chatRuns.add(x + 2, new ChatMessage.ChatRun(ChatMessage.ChatRunType.TEXT, end));
//                    }
//
//                    chatRuns.add(x + 1, new ChatMessage.ChatRun(ChatMessage.ChatRunType.ICON, middle));
//
//                    if (beginning.isEmpty()) {
//                        chatRuns.remove(x);
//                    } else {
//                        chatRun.setData(beginning);
//                    }
                }
            }

            chatIngest.submitChatMessage(new ChatMessage(id, timestamp, author, badges, chatRuns));
        }
    }

    @Override
    public void onLiveStreamIngestFinish(LiveStream liveStream) {
        continuationMap.remove(liveStream.getLiveStreamId());
        chatApiKeyMap.remove(liveStream.getLiveStreamId());
        ingestMap.remove(liveStream.getLiveStreamId());
    }
}
