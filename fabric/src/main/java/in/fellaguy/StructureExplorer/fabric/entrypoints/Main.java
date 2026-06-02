package in.fellaguy.StructureExplorer.fabric.entrypoints;

import in.fellaguy.StructureExplorer.StructureExplorer;
import in.fellaguy.StructureExplorer.commands.SECommand;
import in.fellaguy.StructureExplorer.PlayerNameCache;
import in.fellaguy.StructureExplorer.PlayerStructureData;
import in.fellaguy.StructureExplorer.fabric.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.MutableComponent;

public class Main implements ModInitializer {
    private int tickCounter = 0;
    private final Map<UUID, ChunkPos> lastCheckedChunk = new HashMap<>();

    @Override
    public void onInitialize() {
        ModConfig.load(FabricLoader.getInstance().getConfigDir());
        StructureExplorer.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, dedicated) ->
            SECommand.createCommand(dispatcher,
                () -> ModConfig.load(FabricLoader.getInstance().getConfigDir())));

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

                Set<Identifier> known = data.getStructuresForPlayer(player.getUUID());

                List<StructureStart> starts = player.level()
                    .structureManager()
                    .startsForStructure(currentChunk, s -> true);

                for (StructureStart start : starts) {
                    Identifier key = structureRegistry.getKey(start.getStructure());
                    if (key == null || known.contains(key)) continue;
                    if (!start.getBoundingBox().isInside(player.blockPosition())) continue;

                    int discoverersBefore = data.getPlayersForStructure(key).size();
                    data.add(player.getUUID(), key);
                    int visited = known.size() + 1;

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
                }
            }
        });
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