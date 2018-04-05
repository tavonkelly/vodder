package me.tavon.vodder.driver.chat.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class FrankerFaceZEmoteRegistry extends EmoteRegistry {

    public FrankerFaceZEmoteRegistry(File cacheFolder, OkHttpClient client) {
        super(cacheFolder, client);
    }

    @Override
    public String getName() {
        return "FrankerFaceZ";
    }

    @Override
    Map<String, String> downloadEmotes() throws Exception {
        Request request = new Request.Builder()
                .url("https://api.frankerfacez.com/v1/emoticons?sort=count-desc&per_page=50") // Get to 50 emotes
                .get()
                .addHeader("Cache-Control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            throw new Exception("got non-200 or empty body");
        }

        Map<String, String> emoteMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
            JsonElement element = new JsonParser().parse(reader);
            JsonObject object = element.getAsJsonObject();
            JsonArray emoteArr = object.getAsJsonArray("emoticons");

            for (int i = 0; i < emoteArr.size(); i++) {
                JsonObject emoteObj = emoteArr.get(i).getAsJsonObject();

                emoteMap.put(" " + emoteObj.get("name").getAsString() + " "
                        , "https:" + emoteObj.getAsJsonObject("urls").get("1").getAsString());
            }
        }

        return emoteMap;
    }
}
