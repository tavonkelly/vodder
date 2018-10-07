package me.tavon.vodder.driver.chat.emote;

import com.google.gson.reflect.TypeToken;
import me.tavon.vodder.Vodder;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public abstract class EmoteRegistry {

    private File cacheFolder;
    private Map<String, String> emoteMap;
    OkHttpClient client;

    public EmoteRegistry(File cacheFolder, OkHttpClient client) {
        this.cacheFolder = cacheFolder;
        this.client = client;
    }

    public void init() throws Exception {
        File file = new File(cacheFolder, this.getName().toLowerCase()
                .replace(" ", "_") + "_emotes" + ".json");

        if (!file.exists() || !file.isFile()) {
            Map<String, String> emotes = downloadEmotes();

            if (emotes == null) {
                throw new RuntimeException("downloadEmotes returned null");
            }

            emoteMap = new HashMap<>(emotes);

            try (FileWriter writer = new FileWriter(file)) {
                Vodder.GSON.toJson(emoteMap, writer);
            }

            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type mapType = new TypeToken<Map<String, String>>() {
            }.getType();
            emoteMap = Vodder.GSON.fromJson(reader, mapType);
        }
    }

    public abstract String getName();

    abstract Map<String, String> downloadEmotes() throws Exception;

    public Map<String, String> getEmoteMap() {
        return emoteMap;
    }

}
