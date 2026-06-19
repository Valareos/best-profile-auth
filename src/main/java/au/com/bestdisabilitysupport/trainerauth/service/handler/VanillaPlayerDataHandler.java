package au.com.bestdisabilitysupport.trainerauth.service.handler;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class VanillaPlayerDataHandler implements ProfileDataHandler {
    private final MinecraftServer server;

    public VanillaPlayerDataHandler(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public void saveLivePlayerToSnapshot(ServerPlayerEntity player, UUID liveUuid, Path snapshotRoot) throws IOException {
        Files.createDirectories(snapshotRoot);

        NbtCompound playerNbt = new NbtCompound();
        player.writeNbt(playerNbt);
        NbtIo.writeCompressed(playerNbt, livePlayerdata(liveUuid));

        copyIfExists(livePlayerdata(liveUuid), snapshotRoot.resolve("playerdata.dat"));
        copyIfExists(livePlayerdataOld(liveUuid), snapshotRoot.resolve("playerdata.dat_old"));
    }

    @Override
    public void restoreSnapshotToLiveUuid(UUID liveUuid, Path snapshotRoot) throws IOException {
        Files.createDirectories(livePlayerdata(liveUuid).getParent());

        copyIfExists(snapshotRoot.resolve("playerdata.dat"), livePlayerdata(liveUuid));
        copyIfExists(snapshotRoot.resolve("playerdata.dat_old"), livePlayerdataOld(liveUuid));
    }

    @Override
    public void wipeLiveUuidData(UUID liveUuid) throws IOException {
        Files.deleteIfExists(livePlayerdata(liveUuid));
        Files.deleteIfExists(livePlayerdataOld(liveUuid));
    }

    @Override
    public boolean hasSnapshotData(Path snapshotRoot) {
        return Files.exists(snapshotRoot.resolve("playerdata.dat"))
                || Files.exists(snapshotRoot.resolve("playerdata.dat_old"));
    }

    private Path worldFolder() {
        return server.getRunDirectory().resolve("world");
    }

    private Path livePlayerdata(UUID uuid) {
        return worldFolder().resolve("playerdata").resolve(uuid.toString() + ".dat");
    }

    private Path livePlayerdataOld(UUID uuid) {
        return worldFolder().resolve("playerdata").resolve(uuid.toString() + ".dat_old");
    }

    private static void copyIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
