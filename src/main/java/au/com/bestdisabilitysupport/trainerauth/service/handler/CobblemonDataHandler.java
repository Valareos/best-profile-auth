package au.com.bestdisabilitysupport.trainerauth.service.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

public final class CobblemonDataHandler implements ProfileDataHandler {
    private final MinecraftServer server;

    public CobblemonDataHandler(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "cobblemon";
    }

    @Override
    public void saveLivePlayerToSnapshot(ServerPlayerEntity player, UUID liveUuid, Path snapshotRoot) throws IOException {
        Files.createDirectories(snapshotRoot);
        Files.createDirectories(snapshotRoot.resolve("party"));
        Files.createDirectories(snapshotRoot.resolve("pc"));

        copyIfExists(liveCobblemonPlayerData(liveUuid), snapshotRoot.resolve("cobblemonplayerdata.json"));
        copyIfExists(liveCobblemonPlayerDataOld(liveUuid), snapshotRoot.resolve("cobblemonplayerdata.json.old"));

        copyAllMatchingRecursive(livePartyDir(liveUuid), liveUuid.toString(), snapshotRoot.resolve("party"));
        copyAllMatchingRecursive(livePcDir(liveUuid), liveUuid.toString(), snapshotRoot.resolve("pc"));
    }

    @Override
    public void restoreSnapshotToLiveUuid(UUID liveUuid, Path snapshotRoot) throws IOException {
        Files.createDirectories(liveCobblemonPlayerData(liveUuid).getParent());
        Files.createDirectories(livePartyDir(liveUuid));
        Files.createDirectories(livePcDir(liveUuid));

        copyIfExists(snapshotRoot.resolve("cobblemonplayerdata.json"), liveCobblemonPlayerData(liveUuid));

        if (Files.exists(snapshotRoot.resolve("cobblemonplayerdata.json.old"))) {
            copyIfExists(snapshotRoot.resolve("cobblemonplayerdata.json.old"), liveCobblemonPlayerDataOld(liveUuid));
        } else if (Files.exists(snapshotRoot.resolve("cobblemonplayerdata.json"))) {
            copyIfExists(snapshotRoot.resolve("cobblemonplayerdata.json"), liveCobblemonPlayerDataOld(liveUuid));
        }

        restoreAllMatching(snapshotRoot.resolve("party"), livePartyDir(liveUuid), liveUuid.toString());
        restoreAllMatching(snapshotRoot.resolve("pc"), livePcDir(liveUuid), liveUuid.toString());

        if (Files.exists(snapshotRoot.resolve("playerparty.json"))) {
            copyIfExists(snapshotRoot.resolve("playerparty.json"), livePartyDir(liveUuid).resolve(liveUuid.toString() + ".json"));
        }
        if (Files.exists(snapshotRoot.resolve("pcstore.json"))) {
            copyIfExists(snapshotRoot.resolve("pcstore.json"), livePcDir(liveUuid).resolve(liveUuid.toString() + ".json"));
        }
    }

    @Override
    public void wipeLiveUuidData(UUID liveUuid) throws IOException {
        Files.deleteIfExists(liveCobblemonPlayerData(liveUuid));
        Files.deleteIfExists(liveCobblemonPlayerDataOld(liveUuid));

        deleteAllMatchingRecursive(livePartyDir(liveUuid), liveUuid.toString());
        deleteAllMatchingRecursive(livePcDir(liveUuid), liveUuid.toString());
    }

    @Override
    public boolean hasSnapshotData(Path snapshotRoot) {
        return Files.exists(snapshotRoot.resolve("cobblemonplayerdata.json"))
                || Files.exists(snapshotRoot.resolve("cobblemonplayerdata.json.old"))
                || directoryHasRegularFiles(snapshotRoot.resolve("party"))
                || directoryHasRegularFiles(snapshotRoot.resolve("pc"));
    }

    private Path worldFolder() {
        return server.getRunDirectory().resolve("world");
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
            Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
                            Files.copy(path, snapshotDir.resolve(path.getFileName().toString()),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
                            Files.copy(path, liveDir.resolve(replaced),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static boolean directoryHasRegularFiles(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return false;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
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
