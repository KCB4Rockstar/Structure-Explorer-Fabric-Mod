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
import net.minecraft.server.packs.PackType;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.MutableComponent;

public class Main implements ModInitializer {
    private int tickCounter = 0;
    private final Map<UUID, ChunkPos> lastCheckedChunk = new HashMap<>();

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
                            long seed = player.level().getRandom().nextLong();
                            player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 2.0f, seed));
                            player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 2.0f, seed));
                        }

                    } else if (ModConfig.get().trackInstances) {
                        if (!data.hasInstance(player.getUUID(), key, origin)) {
                            data.addInstance(player.getUUID(), key, origin, now);
                            if (ModConfig.get().sounds.newInstanceSound) {
                                long seed = player.level().getRandom().nextLong();
                                player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.2f, 2.0f, seed));
                                player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.2f, 2.0f, seed));
                            }
                        } else {
                            data.updateTimestamp(player.getUUID(), key, origin, now);
                        }
                    }
                }
            }
        });
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
