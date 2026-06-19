package au.com.bestdisabilitysupport.trainerauth.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MigrationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final Path path;
    private final Map<String, String> migrated;

    public MigrationStore(MinecraftServer server) {
        this.path = server.getRunDirectory()
                .resolve("config")
                .resolve("best-trainer-auth")
                .resolve("migration-markers.json");
        this.migrated = load();
    }

    public synchronized boolean hasMigrated(UUID uuid) {
        return migrated.containsKey(uuid.toString());
    }

    public synchronized Optional<String> migratedProfile(UUID uuid) {
        return Optional.ofNullable(migrated.get(uuid.toString()));
    }

    public synchronized void markMigrated(UUID uuid, String profileKey) {
        migrated.put(uuid.toString(), profileKey);
        save();
    }

    private Map<String, String> load() {
        try {
            Files.createDirectories(path.getParent());

            if (Files.notExists(path)) {
                Files.writeString(path, "{}");
                return new HashMap<>();
            }

            String json = Files.readString(path);
            Map<String, String> loaded = GSON.fromJson(json, MAP_TYPE);
            return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load migration markers: " + path, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(migrated, MAP_TYPE));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save migration markers: " + path, e);
        }
    }
}
