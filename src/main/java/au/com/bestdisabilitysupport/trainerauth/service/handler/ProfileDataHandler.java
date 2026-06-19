package au.com.bestdisabilitysupport.trainerauth.service.handler;

import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public interface ProfileDataHandler {
    String id();

    void saveLivePlayerToSnapshot(ServerPlayerEntity player, UUID liveUuid, Path snapshotRoot) throws IOException;

    void restoreSnapshotToLiveUuid(UUID liveUuid, Path snapshotRoot) throws IOException;

    void wipeLiveUuidData(UUID liveUuid) throws IOException;

    boolean hasSnapshotData(Path snapshotRoot);
}
