package au.com.bestdisabilitysupport.trainerauth.command;

import au.com.bestdisabilitysupport.trainerauth.BestTrainerAuthMod;
import au.com.bestdisabilitysupport.trainerauth.model.TrainerAccount;
import au.com.bestdisabilitysupport.trainerauth.util.PinHasher;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;

public final class TrainerCommand {
    private TrainerCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        registerRoot(dispatcher, "trainer");
        registerRoot(dispatcher, "profile");
    }

    private static void registerRoot(CommandDispatcher<ServerCommandSource> dispatcher, String root) {
        dispatcher.register(
                CommandManager.literal(root)

                        .then(CommandManager.literal("login")
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .then(CommandManager.argument("password", StringArgumentType.word())
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                    String key = StringArgumentType.getString(context, "key");
                                                    String password = StringArgumentType.getString(context, "password");

                                                    String currentTrainer = BestTrainerAuthMod.sessionService().state(player.getUuid())
                                                            .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                                                            .orElse(null);

                                                    if (currentTrainer != null) {
                                                        throw new SimpleCommandExceptionType(
                                                                Text.literal("You are already logged into profile: " + currentTrainer + ". Use /" + root + " logout first.")
                                                        ).create();
                                                    }

                                                    TrainerAccount account = BestTrainerAuthMod.trainerStore().get(key)
                                                            .orElseThrow(() -> new SimpleCommandExceptionType(
                                                                    Text.literal("Profile key not found.")
                                                            ).create());

                                                    if (!account.enabled()) {
                                                        throw new SimpleCommandExceptionType(
                                                                Text.literal("Profile key is disabled.")
                                                        ).create();
                                                    }

                                                    if (!PinHasher.matches(password, account.pinHash())) {
                                                        throw new SimpleCommandExceptionType(
                                                                Text.literal("Incorrect password.")
                                                        ).create();
                                                    }

                                                    BestTrainerAuthMod.trainerBridge().requestLogin(player.getUuid(), account.key());
                                                    disconnectNextTick(player, "Profile " + account.key() + " selected. Reconnect now.");
                                                    return 1;
                                                }))
                                )
                        )

                        .then(CommandManager.literal("logout")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

                                    String trainerKey = BestTrainerAuthMod.sessionService().state(player.getUuid())
                                            .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                                            .orElse(null);

                                    if (trainerKey == null) {
                                        throw new SimpleCommandExceptionType(
                                                Text.literal("You are not logged into a profile.")
                                        ).create();
                                    }

                                    boolean stillExists = BestTrainerAuthMod.trainerStore().exists(trainerKey);
                                    BestTrainerAuthMod.trainerBridge().onDisconnect(player, trainerKey, stillExists);
                                    BestTrainerAuthMod.sessionService().clear(player.getUuid());

                                    disconnectNextTick(player, "Profile session saved. Reconnect to choose another profile.");
                                    return 1;
                                })
                        )

                        .then(CommandManager.literal("whoami")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    String trainerKey = BestTrainerAuthMod.sessionService().state(player.getUuid())
                                            .flatMap(state -> java.util.Optional.ofNullable(state.trainerKey()))
                                            .orElse("none selected");
                                    context.getSource().sendFeedback(() -> Text.literal("Profile: " + trainerKey), false);
                                    return 1;
                                })
                        )

                        .then(CommandManager.literal("create")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .then(CommandManager.argument("password", StringArgumentType.word())
                                                .executes(context -> {
                                                    String key = StringArgumentType.getString(context, "key");
                                                    String password = StringArgumentType.getString(context, "password");

                                                    if (BestTrainerAuthMod.trainerStore().exists(key)) {
                                                        throw new SimpleCommandExceptionType(
                                                                Text.literal("Profile key already exists.")
                                                        ).create();
                                                    }

                                                    TrainerAccount created = BestTrainerAuthMod.trainerStore()
                                                            .create(key, PinHasher.hash(password), context.getSource().getName());
                                                    context.getSource().sendFeedback(() -> Text.literal("Created profile: " + created.key()), true);
                                                    return 1;
                                                }))
                                )
                        )

                        .then(CommandManager.literal("delete")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");

                                            boolean removed = BestTrainerAuthMod.trainerStore().delete(key);
                                            boolean removedData = BestTrainerAuthMod.trainerBridge().deleteTrainerData(key);

                                            if (!removed && !removedData) {
                                                throw new SimpleCommandExceptionType(
                                                        Text.literal("Profile key not found.")
                                                ).create();
                                            }

                                            context.getSource().sendFeedback(() -> Text.literal("Deleted profile: " + key), true);
                                            return 1;
                                        }))
                        )

                        .then(CommandManager.literal("setpassword")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .then(CommandManager.argument("password", StringArgumentType.word())
                                                .executes(context -> {
                                                    String key = StringArgumentType.getString(context, "key");
                                                    String password = StringArgumentType.getString(context, "password");
                                                    BestTrainerAuthMod.trainerStore().updatePin(key, PinHasher.hash(password));
                                                    context.getSource().sendFeedback(() -> Text.literal("Updated password for: " + key), true);
                                                    return 1;
                                                }))
                                )
                        )

                        // Legacy alias
                        .then(CommandManager.literal("setpin")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .then(CommandManager.argument("password", StringArgumentType.word())
                                                .executes(context -> {
                                                    String key = StringArgumentType.getString(context, "key");
                                                    String password = StringArgumentType.getString(context, "password");
                                                    BestTrainerAuthMod.trainerStore().updatePin(key, PinHasher.hash(password));
                                                    context.getSource().sendFeedback(() -> Text.literal("Updated password for: " + key), true);
                                                    return 1;
                                                }))
                                )
                        )

                        .then(CommandManager.literal("disable")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");
                                            BestTrainerAuthMod.trainerStore().setEnabled(key, false);
                                            context.getSource().sendFeedback(() -> Text.literal("Disabled profile: " + key), true);
                                            return 1;
                                        })
                                )
                        )

                        .then(CommandManager.literal("enable")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");
                                            BestTrainerAuthMod.trainerStore().setEnabled(key, true);
                                            context.getSource().sendFeedback(() -> Text.literal("Enabled profile: " + key), true);
                                            return 1;
                                        })
                                )
                        )

                        .then(CommandManager.literal("list")
                                .requires(source -> source.hasPermissionLevel(BestTrainerAuthMod.config().adminBypassPermissionLevel()))
                                .executes(context -> {
                                    String joined = BestTrainerAuthMod.trainerStore().all().stream()
                                            .sorted(Comparator.comparing(TrainerAccount::key))
                                            .map(account -> account.key() + (account.enabled() ? "" : " [disabled]"))
                                            .reduce((left, right) -> left + ", " + right)
                                            .orElse("No profiles exist yet.");
                                    context.getSource().sendFeedback(() -> Text.literal(joined), false);
                                    return 1;
                                })
                        )

                        .then(CommandManager.literal("help")
                                .executes(context -> {
                                    context.getSource().sendFeedback(
                                            () -> Text.literal(
                                                    "/" + root + " login <key> <password>, " +
                                                    "/" + root + " logout, " +
                                                    "/" + root + " whoami, " +
                                                    "/" + root + " create <key> <password>, " +
                                                    "/" + root + " delete <key>, " +
                                                    "/" + root + " setpassword <key> <password>"
                                            ),
                                            false
                                    );
                                    return 1;
                                })
                        )
        );
    }

    private static void disconnectNextTick(ServerPlayerEntity player, String message) {
        player.getServer().execute(() -> player.networkHandler.disconnect(Text.literal(message)));
    }
}
