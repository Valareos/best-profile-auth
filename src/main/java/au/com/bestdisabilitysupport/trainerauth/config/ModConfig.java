
package au.com.bestdisabilitysupport.trainerauth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ModConfig(
        int configVersion,
        int adminBypassPermissionLevel,
        boolean lockMovement,
        boolean lockInteractions,
        boolean lockBlockBreaking,
        boolean lockCombat,
        long autosaveIntervalTicks,
        int maxBackupsPerProfile
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "config.json";
    private static final int CURRENT_CONFIG_VERSION = 2;

    public static ModConfig defaults() {
        return new ModConfig(
                CURRENT_CONFIG_VERSION,
                4,
                true,
                true,
                true,
                true,
                6000L,
                10
        );
    }

    public static ModConfig loadOrCreate(MinecraftServer server) {
        Path path = configPath(server);

        try {
            Files.createDirectories(path.getParent());

            if (Files.notExists(path)) {
                ModConfig defaults = defaults();
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }

            String json = Files.readString(path);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root == null) {
                ModConfig defaults = defaults();
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }

            ModConfig merged = mergeWithDefaults(root);
            Files.writeString(path, GSON.toJson(merged));
            return merged;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + path, e);
        }
    }

    private static ModConfig mergeWithDefaults(JsonObject root) {
        ModConfig defaults = defaults();

        int configVersion = getInt(root, "configVersion", defaults.configVersion());
        int adminBypassPermissionLevel = getInt(root, "adminBypassPermissionLevel", defaults.adminBypassPermissionLevel());
        boolean lockMovement = getBoolean(root, "lockMovement", defaults.lockMovement());
        boolean lockInteractions = getBoolean(root, "lockInteractions", defaults.lockInteractions());
        boolean lockBlockBreaking = getBoolean(root, "lockBlockBreaking", defaults.lockBlockBreaking());
        boolean lockCombat = getBoolean(root, "lockCombat", defaults.lockCombat());
        long autosaveIntervalTicks = getLong(root, "autosaveIntervalTicks", defaults.autosaveIntervalTicks());
        int maxBackupsPerProfile = getInt(root, "maxBackupsPerProfile", defaults.maxBackupsPerProfile());

        return new ModConfig(
                CURRENT_CONFIG_VERSION,
                adminBypassPermissionLevel,
                lockMovement,
                lockInteractions,
                lockBlockBreaking,
                lockCombat,
                autosaveIntervalTicks,
                maxBackupsPerProfile
        );
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        try {
            return root.has(key) ? root.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long getLong(JsonObject root, String key, long fallback) {
        try {
            return root.has(key) ? root.get(key).getAsLong() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        try {
            return root.has(key) ? root.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Path configPath(MinecraftServer server) {
        return server.getRunDirectory()
                .resolve("config")
                .resolve("best-trainer-auth")
                .resolve(FILE_NAME);
    }
}
