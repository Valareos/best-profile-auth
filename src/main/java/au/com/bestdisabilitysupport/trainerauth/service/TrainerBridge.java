package au.com.bestdisabilitysupport.trainerauth.service;

import au.com.bestdisabilitysupport.trainerauth.config.ModConfig;
import au.com.bestdisabilitysupport.trainerauth.service.handler.CobblemonDataHandler;
import au.com.bestdisabilitysupport.trainerauth.service.handler.ProfileDataHandler;
import au.com.bestdisabilitysupport.trainerauth.service.handler.VanillaPlayerDataHandler;
import au.com.bestdisabilitysupport.trainerauth.util.KeyNormalizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class TrainerBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("best-trainer-auth");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final MinecraftServer server;
    private final ModConfig config;
    private final TrainerSelectionStore selectionStore;
    private final VanillaPlayerDataHandler vanillaHandler;
    private final CobblemonDataHandler cobblemonHandler;
    private final List<ProfileDataHandler> handlers;

    public TrainerBridge(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.selectionStore = new TrainerSelectionStore(server);
        this.vanillaHandler = new VanillaPlayerDataHandler(server);
        this.cobblemonHandler = new CobblemonDataHandler(server);
        this.handlers = List.of(vanillaHandler, cobblemonHandler);
    }

    public void requestLogin(UUID liveUuid, String trainerKey) {
        selectionStore.setPending(liveUuid, trainerKey);
    }

    public Optional<String> prepareForJoin(UUID liveUuid) {
        Optional<String> pending = selectionStore.consumePending(liveUuid);
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        String trainerKey = pending.get();
        ensureTrainerFolder(trainerKey);

        Path snapshot = snapshotFolder(trainerKey);

        if (hasAnySnapshotData(snapshot)) {
            restoreSnapshotToLiveUuid(snapshot, liveUuid);
        } else {
            wipeLiveUuidData(liveUuid);
        }

        selectionStore.setActivated(liveUuid, trainerKey);
        return Optional.of(trainerKey);
    }

    public Optional<String> consumeActivatedTrainer(UUID liveUuid) {
        return selectionStore.consumeActivated(liveUuid);
    }

    public void onDisconnect(ServerPlayerEntity player, String trainerKey, boolean stillExists) {
        try {
            if (stillExists) {
                ensureTrainerFolder(trainerKey);
                saveLivePlayerToSnapshot(player, snapshotFolder(trainerKey), true, trainerKey);
            }
        } finally {
            selectionStore.clearAll(player.getUuid());
        }
    }

    public void autosaveActiveProfile(ServerPlayerEntity player, String trainerKey, boolean stillExists) {
        if (!stillExists) return;
        ensureTrainerFolder(trainerKey);
        saveLivePlayerToSnapshot(player, snapshotFolder(trainerKey), true, trainerKey);
    }

    public boolean deleteTrainerData(String trainerKey) {
        Path trainerFolder = trainerFolder(trainerKey);
        if (!Files.exists(trainerFolder)) return false;

        try {
            Files.walkFileTree(trainerFolder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public UUID stableTrainerUuid(String trainerKey) {
        String normalized = KeyNormalizer.normalize(trainerKey);
        return UUID.nameUUIDFromBytes(("best-trainer:" + normalized).getBytes(StandardCharsets.UTF_8));
    }

    public List<String> activeHandlerIds() {
        return handlers.stream().map(ProfileDataHandler::id).toList();
    }

    public boolean hasLiveData(UUID uuid) {
        try {
            Path world = server.getRunDirectory().resolve("world");

            if (Files.exists(world.resolve("playerdata").resolve(uuid + ".dat"))) return true;

            String prefix = uuid.toString().substring(0, 2);

            if (Files.exists(world.resolve("cobblemonplayerdata").resolve(prefix).resolve(uuid + ".json"))) return true;
            if (Files.exists(world.resolve("pokemon/playerpartystore").resolve(prefix).resolve(uuid + ".dat"))) return true;
            if (Files.exists(world.resolve("pokemon/pcstore").resolve(prefix).resolve(uuid + ".dat"))) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void importCurrentLiveData(ServerPlayerEntity player, String trainerKey) {
        ensureTrainerFolder(trainerKey);
        Path snapshot = snapshotFolder(trainerKey);

        try {
            wipeSnapshotFolder(snapshot);

            for (ProfileDataHandler handler : handlers) {
                Path root = snapshot.resolve(handler.id());
                Files.createDirectories(root);
                handler.saveLivePlayerToSnapshot(player, player.getUuid(), root);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path trainerFolder(String trainerKey) {
        return server.getRunDirectory()
                .resolve("config")
                .resolve("best-trainer-auth")
                .resolve("trainers")
                .resolve(KeyNormalizer.normalize(trainerKey));
    }

    private Path snapshotFolder(String trainerKey) {
        return trainerFolder(trainerKey).resolve("snapshot");
    }

    private void ensureTrainerFolder(String trainerKey) {
        try {
            Files.createDirectories(snapshotFolder(trainerKey));
            Files.createDirectories(trainerFolder(trainerKey).resolve("backups"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasAnySnapshotData(Path snapshot) {
        return hasLegacyVanillaData(snapshot)
                || hasLegacyCobblemonData(snapshot)
                || handlers.stream().anyMatch(h -> h.hasSnapshotData(snapshot.resolve(h.id())));
    }

    private static boolean hasLegacyVanillaData(Path snapshot) {
        return Files.exists(snapshot.resolve("playerdata.dat"))
                || Files.exists(snapshot.resolve("playerdata.dat_old"));
    }

    private static boolean hasLegacyCobblemonData(Path snapshot) {
        return Files.exists(snapshot.resolve("cobblemonplayerdata.json"))
                || Files.exists(snapshot.resolve("cobblemonplayerdata.json.old"))
                || Files.exists(snapshot.resolve("party"))
                || Files.exists(snapshot.resolve("pc"));
    }

    private void restoreSnapshotToLiveUuid(Path snapshot, UUID uuid) {
        try {
            wipeLiveUuidData(uuid);

            // v0.2.0 layout: snapshot/party, snapshot/pc, snapshot/cobblemonplayerdata.json, etc.
            if (hasLegacyVanillaData(snapshot)) {
                vanillaHandler.restoreSnapshotToLiveUuid(uuid, snapshot);
            }
            if (hasLegacyCobblemonData(snapshot)) {
                cobblemonHandler.restoreSnapshotToLiveUuid(uuid, snapshot);
            }

            // v0.3.0 handler layout overlays newer saves when present
            for (ProfileDataHandler handler : handlers) {
                Path root = snapshot.resolve(handler.id());
                if (handler.hasSnapshotData(root)) {
                    handler.restoreSnapshotToLiveUuid(uuid, root);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed restoring trainer snapshot for " + uuid, e);
        }
    }

    private void wipeLiveUuidData(UUID uuid) {
        try {
            for (ProfileDataHandler handler : handlers) {
                handler.wipeLiveUuidData(uuid);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed wiping live UUID data for " + uuid, e);
        }
    }

    private void saveLivePlayerToSnapshot(ServerPlayerEntity player, Path snapshot, boolean makeBackup, String trainerKey) {
        try {
            if (makeBackup) {
                backupExistingSnapshot(snapshot, trainerKey);
            }

            Files.createDirectories(snapshot);

            for (ProfileDataHandler handler : handlers) {
                Path root = snapshot.resolve(handler.id());
                Files.createDirectories(root);
                handler.saveLivePlayerToSnapshot(player, player.getUuid(), root);
            }

            if (makeBackup) {
                trimOldBackups(snapshot.getParent().resolve("backups"), trainerKey);
            }

            LOGGER.info(
                    "[BestProfileAuth] Saved profile '{}' for {} ({})",
                    trainerKey,
                    player.getName().getString(),
                    player.getUuid()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed saving trainer snapshot for " + player.getUuid(), e);
        }
    }

    private void backupExistingSnapshot(Path snapshot, String trainerKey) throws IOException {
        if (!Files.exists(snapshot) || !hasAnySnapshotData(snapshot)) {
            return;
        }

        Path backups = snapshot.getParent().resolve("backups");
        Files.createDirectories(backups);

        Path backupTarget = backups.resolve(BACKUP_STAMP.format(LocalDateTime.now()));
        copyDirectory(snapshot, backupTarget);

        LOGGER.info(
                "[BestProfileAuth] Created backup {} for profile '{}'",
                backupTarget.getFileName(),
                trainerKey
        );
    }

    private void trimOldBackups(Path backupsDir, String trainerKey) throws IOException {
        if (!Files.exists(backupsDir) || !Files.isDirectory(backupsDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(backupsDir)) {
            List<Path> dirs = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName, Comparator.reverseOrder()))
                    .toList();

            for (int i = config.maxBackupsPerProfile(); i < dirs.size(); i++) {
                Path removed = dirs.get(i);
                deleteDirectory(removed);
                LOGGER.info(
                        "[BestProfileAuth] Removed old backup {} for profile '{}' (retention limit: {})",
                        removed.getFileName(),
                        trainerKey,
                        config.maxBackupsPerProfile()
                );
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path current, IOException exc) throws IOException {
                Files.deleteIfExists(current);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void wipeSnapshotFolder(Path snapshot) throws IOException {
        if (!Files.exists(snapshot)) return;

        try (Stream<Path> stream = Files.walk(snapshot)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            if (!p.equals(snapshot)) Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
