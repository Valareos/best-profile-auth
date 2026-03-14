package au.com.bestdisabilitysupport.trainerauth;

import au.com.bestdisabilitysupport.trainerauth.command.TrainerCommand;
import au.com.bestdisabilitysupport.trainerauth.config.ModConfig;
import au.com.bestdisabilitysupport.trainerauth.service.LockService;
import au.com.bestdisabilitysupport.trainerauth.service.TrainerBridge;
import au.com.bestdisabilitysupport.trainerauth.service.TrainerSessionService;
import au.com.bestdisabilitysupport.trainerauth.service.TrainerStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class BestTrainerAuthMod implements ModInitializer {
    public static final String MOD_ID = "best-trainer-auth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;
    private static ModConfig config;
    private static TrainerStore trainerStore;
    private static TrainerSessionService sessionService;
    private static TrainerBridge trainerBridge;
    private static long tickCounter = 0L;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);

        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
            server = minecraftServer;
            config = ModConfig.loadOrCreate(minecraftServer);
            trainerStore = new TrainerStore(minecraftServer);
            sessionService = new TrainerSessionService();
            trainerBridge = new TrainerBridge(minecraftServer, config);
            tickCounter = 0L;
            LOGGER.info("{} loaded successfully.", MOD_ID);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            if (sessionService == null || trainerBridge == null || trainerStore == null) {
                return;
            }

            for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
                sessionService.state(player.getUuid())
                        .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                        .ifPresent(trainerKey -> {
                            boolean stillExists = trainerStore.exists(trainerKey);
                            trainerBridge.onDisconnect(player, trainerKey, stillExists);
                        });
            }
        });

        CommandRegistrationCallback.EVENT.register(TrainerCommand::register);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> {
            ServerPlayerEntity player = handler.getPlayer();

            Optional<String> activated = trainerBridge.consumeActivatedTrainer(player.getUuid());
            if (activated.isPresent()) {
                sessionService.setLoggedIn(player, activated.get());
                player.sendMessage(Text.literal("Logged in as trainer: " + activated.get()), false);
            } else {
                sessionService.markPending(player, player.getPos(), player.getYaw(), player.getPitch());
		player.sendMessage(Text.literal("You must login with a profile."), false);
		player.sendMessage(Text.literal("Use /profile login <key> <password>"), false);            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
            ServerPlayerEntity player = handler.getPlayer();

            sessionService.state(player.getUuid())
                    .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                    .ifPresent(trainerKey -> {
                        boolean stillExists = trainerStore.exists(trainerKey);
                        trainerBridge.onDisconnect(player, trainerKey, stillExists);
                    });

            sessionService.clear(player.getUuid());
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            sessionService.copyConnectionState(oldPlayer, newPlayer);
        });

        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
            if (sessionService == null || config == null || trainerBridge == null || trainerStore == null) {
                return;
            }

            tickCounter++;

            for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
                LockService.tickLockedPlayer(player, sessionService, config);
            }

            long autosaveInterval = config.autosaveIntervalTicks();
            if (autosaveInterval > 0 && tickCounter % autosaveInterval == 0) {
                for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
                    sessionService.state(player.getUuid())
                            .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                            .ifPresent(trainerKey -> {
                                boolean stillExists = trainerStore.exists(trainerKey);
                                trainerBridge.autosaveActiveProfile(player, trainerKey, stillExists);
                            });
                }

                LOGGER.info("[BestTrainerAuth] Autosave completed for active profiles.");
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                (player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService))
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                (player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService))
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                (player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService))
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                (player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService))
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService))
        );

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && LockService.isLocked(serverPlayer, sessionService)) {
                ItemStack held = player.getStackInHand(hand);
                return TypedActionResult.fail(held);
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (LockService.isLocked(player, sessionService)) {
                    return false;
                }
            }
            return true;
        });
    }

    public static MinecraftServer server() {
        return server;
    }

    public static ModConfig config() {
        return config;
    }

    public static TrainerStore trainerStore() {
        return trainerStore;
    }

    public static TrainerSessionService sessionService() {
        return sessionService;
    }

    public static TrainerBridge trainerBridge() {
        return trainerBridge;
    }
}

