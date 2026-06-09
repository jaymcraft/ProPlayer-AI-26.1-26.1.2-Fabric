package net.shasankp000.Entity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.shasankp000.LauncherDetection.LauncherEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BotSkinHelper {
    private static final Gson GSON = new Gson();
    private static final Path BOT_PROFILE_PATH = Paths.get(LauncherEnvironment.getStorageDirectory("config") + File.separator + "settings.json5");

    private BotSkinHelper() {
    }

    public static boolean isRegisteredBot(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }

        try (Reader reader = Files.newBufferedReader(BOT_PROFILE_PATH)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("botGameProfile") || !root.get("botGameProfile").isJsonObject()) {
                return false;
            }

            JsonObject profiles = root.getAsJsonObject("botGameProfile");
            for (String botName : profiles.keySet()) {
                if (botName.equalsIgnoreCase(playerName)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }

        return false;
    }
}
