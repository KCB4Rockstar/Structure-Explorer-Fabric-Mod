package in.fellaguy.StructureExplorer.fabric.entrypoints;

import in.fellaguy.StructureExplorer.StructureExplorer;
import in.fellaguy.StructureExplorer.commands.SECommand;
import in.fellaguy.StructureExplorer.PlayerNameCache;
import in.fellaguy.StructureExplorer.PlayerStructureData;
import in.fellaguy.StructureExplorer.fabric.ModConfig;
import in.fellaguy.StructureExplorer.fabric.TranslationLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Main implements ModInitializer {
    private int tickCounter = 0;
    private final Map<UUID, ChunkPos> lastCheckedChunk = new HashMap<>();
    private final List<DelayedSound> delayedSounds = new ArrayList<>();

    private static class DelayedSound {
        int ticksLeft;
        final UUID playerId;
        final Holder<SoundEvent> sound;
        final float volume;
        final float pitch;

        DelayedSound(int ticksLeft, UUID playerId, Holder<SoundEvent> sound, float volume, float pitch) {
            this.ticksLeft = ticksLeft;
            this.playerId = playerId;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    @Override
    public void onInitialize() {
        loadConfig();
        StructureExplorer.init();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new TranslationLoader());

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, dedicated) ->
            SECommand.createCommand(dispatcher, () -> loadConfig()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerNameCache.get(server).update(
                handler.player.getUUID(),
                handler.player.getScoreboardName()
            );
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            lastCheckedChunk.remove(handler.player.getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            processDelayedSounds(server);

            tickCounter++;
            if (tickCounter < ModConfig.get().getCheckIntervalTicks()) return;
            tickCounter = 0;

            Registry<Structure> structureRegistry = server.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);
            int total = structureRegistry.size();
            PlayerStructureData data = PlayerStructureData.get(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ChunkPos currentChunk = new ChunkPos(player.blockPosition());
                if (currentChunk.equals(lastCheckedChunk.get(player.getUUID()))) continue;
                lastCheckedChunk.put(player.getUUID(), currentChunk);

                List<StructureStart> starts = player.level()
                    .structureManager()
                    .startsForStructure(currentChunk, s -> true);

                for (StructureStart start : starts) {
                    Identifier key = structureRegistry.getKey(start.getStructure());
                    if (key == null) continue;
                    if (!start.getBoundingBox().isInside(player.blockPosition())) continue;

                    BlockPos origin = new BlockPos(
                        (start.getBoundingBox().minX() + start.getBoundingBox().maxX()) / 2,
                        (start.getBoundingBox().minY() + start.getBoundingBox().maxY()) / 2,
                        (start.getBoundingBox().minZ() + start.getBoundingBox().maxZ()) / 2
                    );
                    Instant now = Instant.now();

                    if (!data.hasDiscovered(player.getUUID(), key)) {
                        int discoverersBefore = data.getPlayersForStructure(key).size();
                        if (ModConfig.get().trackInstances) {
                            data.addDiscovery(player.getUUID(), key, origin, now);
                        } else {
                            data.addDiscoveryKey(player.getUUID(), key);
                        }
                        int visited = data.getStructuresForPlayer(player.getUUID()).size();

                        MutableComponent msg = SECommand.clickableStructure(key)
                            .append(Component.literal(" [New discovery!] (" + visited + "/" + total + ")")
                                .withStyle(ChatFormatting.GREEN));

                        if (ModConfig.get().showNthDiscoverer) {
                            if (discoverersBefore == 0) {
                                msg.append(Component.literal(" [First Discoverer!]").withStyle(ChatFormatting.AQUA));
                            } else {
                                int nth = discoverersBefore + 1;
                                msg.append(Component.literal(" [" + nth + getOrdinalSuffix(nth) + " Discoverer]").withStyle(ChatFormatting.YELLOW));
                            }
                        }

                        player.sendSystemMessage(msg, false);

                        if (ModConfig.get().sounds.newDiscoverySound) {
                            if (discoverersBefore == 0) {
                                playSound(player, Holder.direct(SoundEvents.PLAYER_LEVELUP), 1.0f, 1.2f);
                            } else {
                                playSound(player, Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), 1.0f, 1.5f);
                            }
                        }

                    } else if (ModConfig.get().trackInstances) {
                        if (!data.hasInstance(player.getUUID(), key, origin)) {
                            data.addInstance(player.getUUID(), key, origin, now);
                            if (ModConfig.get().sounds.newInstanceSound) {
                                playSound(player, Holder.direct(SoundEvents.UI_TOAST_IN), 1.0f, 0.93f);
                                delayedSounds.add(new DelayedSound(5, player.getUUID(), Holder.direct(SoundEvents.UI_TOAST_OUT), 1.0f, 1.36f));
                            }
                        } else {
                            data.updateTimestamp(player.getUUID(), key, origin, now);
                        }
                    }
                }
            }
        });
    }

    private void processDelayedSounds(MinecraftServer server) {
        Iterator<DelayedSound> it = delayedSounds.iterator();
        while (it.hasNext()) {
            DelayedSound ds = it.next();
            if (--ds.ticksLeft <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(ds.playerId);
                if (player != null) {
                    playSound(player, ds.sound, ds.volume, ds.pitch);
                }
                it.remove();
            }
        }
    }

    private static void playSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        long seed = player.level().getRandom().nextLong();
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), volume, pitch, seed));
    }

    private static void loadConfig() {
        ModConfig.load(FabricLoader.getInstance().getConfigDir());
        StructureExplorer.useMonthDayYear = ModConfig.get().useMonthDayYear;
        StructureExplorer.trackInstances = ModConfig.get().trackInstances;
    }

    private static String getOrdinalSuffix(int n) {
        if (n >= 11 && n <= 13) return "th";
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
