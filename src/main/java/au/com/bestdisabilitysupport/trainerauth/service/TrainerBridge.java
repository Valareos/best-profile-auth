package au.com.bestdisabilitysupport.trainerauth.service;

import au.com.bestdisabilitysupport.trainerauth.config.ModConfig;
import au.com.bestdisabilitysupport.trainerauth.util.KeyNormalizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class TrainerBridge {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final MinecraftServer server;
    private final ModConfig config;
    private final TrainerSelectionStore selectionStore;

    public TrainerBridge(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.selectionStore = new TrainerSelectionStore(server);
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
                saveLivePlayerToSnapshot(player, snapshotFolder(trainerKey), true);
            }
        } finally {
            selectionStore.clearAll(player.getUuid());
        }
    }

    public void autosaveActiveProfile(ServerPlayerEntity player, String trainerKey, boolean stillExists) {
        if (!stillExists) {
            return;
        }

        ensureTrainerFolder(trainerKey);
        saveLivePlayerToSnapshot(player, snapshotFolder(trainerKey), true);
    }

    public boolean deleteTrainerData(String trainerKey) {
        Path trainerFolder = trainerFolder(trainerKey);
        if (!Files.exists(trainerFolder)) {
            return false;
        }

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
            throw new RuntimeException("Failed to delete trainer folder for " + trainerKey, e);
        }
    }

    public UUID stableTrainerUuid(String trainerKey) {
        String normalized = KeyNormalizer.normalize(trainerKey);
        return UUID.nameUUIDFromBytes(("best-trainer:" + normalized).getBytes(StandardCharsets.UTF_8));
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

    private Path backupsFolder(String trainerKey) {
        return trainerFolder(trainerKey).resolve("backups");
    }

    private Path worldFolder() {
        return server.getRunDirectory().resolve("world");
    }

    private void ensureTrainerFolder(String trainerKey) {
        try {
            Files.createDirectories(snapshotFolder(trainerKey));
            Files.createDirectories(backupsFolder(trainerKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create trainer folder for " + trainerKey, e);
        }
    }

    private boolean hasAnySnapshotData(Path snapshot) {
        return Files.exists(snapshot.resolve("playerdata.dat"))
                || Files.exists(snapshot.resolve("playerdata.dat_old"))
                || Files.exists(snapshot.resolve("cobblemonplayerdata.json"))
                || Files.exists(snapshot.resolve("cobblemonplayerdata.json.old"))
                || Files.exists(snapshot.resolve("party"))
                || Files.exists(snapshot.resolve("pc"));
    }

    private void saveLivePlayerToSnapshot(ServerPlayerEntity player, Path snapshot, boolean makeBackup) {
        UUID uuid = player.getUuid();

        try {
            if (makeBackup) {
                backupExistingSnapshot(snapshot);
            }

            Files.createDirectories(snapshot);
            Files.createDirectories(snapshot.resolve("party"));
            Files.createDirectories(snapshot.resolve("pc"));

            NbtCompound playerNbt = new NbtCompound();
            player.writeNbt(playerNbt);
            NbtIo.writeCompressed(playerNbt, livePlayerdata(uuid));

            copyIfExists(livePlayerdata(uuid), snapshot.resolve("playerdata.dat"));
            copyIfExists(livePlayerdataOld(uuid), snapshot.resolve("playerdata.dat_old"));

            copyIfExists(liveCobblemonPlayerData(uuid), snapshot.resolve("cobblemonplayerdata.json"));
            copyIfExists(liveCobblemonPlayerDataOld(uuid), snapshot.resolve("cobblemonplayerdata.json.old"));

            copyAllMatchingRecursive(livePartyDir(uuid), uuid.toString(), snapshot.resolve("party"));
            copyAllMatchingRecursive(livePcDir(uuid), uuid.toString(), snapshot.resolve("pc"));

            trimOldBackups(snapshot.getParent().resolve("backups"));
        } catch (IOException e) {
            throw new RuntimeException("Failed saving trainer snapshot for " + uuid, e);
        }
    }

    private void restoreSnapshotToLiveUuid(Path snapshot, UUID uuid) {
        try {
            Files.createDirectories(livePlayerdata(uuid).getParent());
            Files.createDirectories(liveCobblemonPlayerData(uuid).getParent());
            Files.createDirectories(livePartyDir(uuid));
            Files.createDirectories(livePcDir(uuid));

            wipeLiveUuidData(uuid);

            copyIfExists(snapshot.resolve("playerdata.dat"), livePlayerdata(uuid));
            copyIfExists(snapshot.resolve("playerdata.dat_old"), livePlayerdataOld(uuid));

            copyIfExists(snapshot.resolve("cobblemonplayerdata.json"), liveCobblemonPlayerData(uuid));

            if (Files.exists(snapshot.resolve("cobblemonplayerdata.json.old"))) {
                copyIfExists(snapshot.resolve("cobblemonplayerdata.json.old"), liveCobblemonPlayerDataOld(uuid));
            } else if (Files.exists(snapshot.resolve("cobblemonplayerdata.json"))) {
                copyIfExists(snapshot.resolve("cobblemonplayerdata.json"), liveCobblemonPlayerDataOld(uuid));
            }

            restoreAllMatching(snapshot.resolve("party"), livePartyDir(uuid), uuid.toString());
            restoreAllMatching(snapshot.resolve("pc"), livePcDir(uuid), uuid.toString());

            if (Files.exists(snapshot.resolve("playerparty.json"))) {
                copyIfExists(snapshot.resolve("playerparty.json"), livePartyDir(uuid).resolve(uuid.toString() + ".json"));
            }
            if (Files.exists(snapshot.resolve("pcstore.json"))) {
                copyIfExists(snapshot.resolve("pcstore.json"), livePcDir(uuid).resolve(uuid.toString() + ".json"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed restoring trainer snapshot for " + uuid, e);
        }
    }

    private void wipeLiveUuidData(UUID uuid) {
        try {
            Files.deleteIfExists(livePlayerdata(uuid));
            Files.deleteIfExists(livePlayerdataOld(uuid));

            Files.deleteIfExists(liveCobblemonPlayerData(uuid));
            Files.deleteIfExists(liveCobblemonPlayerDataOld(uuid));

            deleteAllMatchingRecursive(livePartyDir(uuid), uuid.toString());
            deleteAllMatchingRecursive(livePcDir(uuid), uuid.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed wiping live UUID data for " + uuid, e);
        }
    }

    private void backupExistingSnapshot(Path snapshot) throws IOException {
        if (!Files.exists(snapshot) || !hasAnySnapshotData(snapshot)) {
            return;
        }

        Path trainerFolder = snapshot.getParent();
        Path backups = trainerFolder.resolve("backups");
        Files.createDirectories(backups);

        Path backupTarget = backups.resolve(BACKUP_STAMP.format(LocalDateTime.now()));
        copyDirectory(snapshot, backupTarget);
    }

    private void trimOldBackups(Path backupsDir) throws IOException {
        if (!Files.exists(backupsDir) || !Files.isDirectory(backupsDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(backupsDir)) {
            java.util.List<Path> dirs = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();

            for (int i = config.maxBackupsPerProfile(); i < dirs.size(); i++) {
                deleteDirectory(dirs.get(i));
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

    private Path livePlayerdata(UUID uuid) {
        return worldFolder().resolve("playerdata").resolve(uuid.toString() + ".dat");
    }

    private Path livePlayerdataOld(UUID uuid) {
        return worldFolder().resolve("playerdata").resolve(uuid.toString() + ".dat_old");
    }

    private Path liveCobblemonPlayerData(UUID uuid) {
        String prefix = uuid.toString().substring(0, 2);
        return worldFolder()
                .resolve("cobblemonplayerdata")
                .resolve(prefix)
                .resolve(uuid.toString() + ".json");
    }

    private Path liveCobblemonPlayerDataOld(UUID uuid) {
        String prefix = uuid.toString().substring(0, 2);
        return worldFolder()
                .resolve("cobblemonplayerdata")
                .resolve(prefix)
                .resolve(uuid.toString() + ".json.old");
    }

    private Path livePartyDir(UUID uuid) {
        String prefix = uuid.toString().substring(0, 2);
        return worldFolder()
                .resolve("pokemon")
                .resolve("playerpartystore")
                .resolve(prefix);
    }

    private Path livePcDir(UUID uuid) {
        String prefix = uuid.toString().substring(0, 2);
        return worldFolder()
                .resolve("pokemon")
                .resolve("pcstore")
                .resolve(prefix);
    }

    private static void copyIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyAllMatchingRecursive(Path dir, String uuidPrefix, Path snapshotDir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        Files.createDirectories(snapshotDir);

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(uuidPrefix))
                    .forEach(path -> {
                        try {
                            Files.copy(
                                    path,
                                    snapshotDir.resolve(path.getFileName().toString()),
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static void restoreAllMatching(Path snapshotDir, Path liveDir, String liveUuid) throws IOException {
        if (!Files.exists(snapshotDir) || !Files.isDirectory(snapshotDir)) {
            return;
        }

        Files.createDirectories(liveDir);

        try (Stream<Path> stream = Files.list(snapshotDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();

                        String replaced;
                        int dot = fileName.indexOf('.');
                        if (dot > 0) {
                            replaced = liveUuid + fileName.substring(dot);
                        } else {
                            replaced = liveUuid + ".json";
                        }

                        try {
                            Files.copy(
                                    path,
                                    liveDir.resolve(replaced),
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static void deleteAllMatchingRecursive(Path dir, String uuidPrefix) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(uuidPrefix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
