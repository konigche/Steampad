package dev.steampad.util;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonUtil {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private JsonUtil() {}

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T loadFromFile(Path path, Class<T> clazz, T defaultValue) {
        if (!Files.exists(path)) return defaultValue;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = GSON.fromJson(r, clazz);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            LogUtil.warn("Failed to load {}, using defaults: {}", path, e.getMessage());
            return defaultValue;
        }
    }

    public static void saveToFile(Path path, Object obj) {
        try {
            Files.createDirectories(path.getParent());
            // Write to a sibling temp file first, then atomically replace the target. A crash or power
            // loss mid-write (this project has hit exactly that before — see the Loom cache corruption
            // incident) would otherwise leave a half-written, unparseable JSON file in place; this way
            // the last good save always survives and only the temp file can end up damaged.
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(obj, w);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LogUtil.error("Failed to save {}", path, e);
        }
    }

    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
