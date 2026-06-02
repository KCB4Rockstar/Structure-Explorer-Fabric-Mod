package in.fellaguy.StructureExplorer.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import in.fellaguy.StructureExplorer.PlayerStructureData;
import in.fellaguy.StructureExplorer.PlayerNameCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public class SECommand {
    private static final int ITEMS_PER_PAGE = 8;
    
    public static void createCommand(CommandDispatcher<CommandSourceStack> dispatcher, Runnable reloadConfig) {
        // /discoveries — list structures for the calling player
        LiteralCommandNode<CommandSourceStack> visitsSource = dispatcher.register(
            Commands.literal("discoveries")
                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                .executes(cs -> {
                    if (!cs.getSource().isPlayer()) {
                        cs.getSource().sendSuccess(() -> Component.literal("A player must run this command."), false);
                        return 0;
                    }
                    ServerPlayer player = cs.getSource().getPlayer();
                    return sendPaginatedStructures(cs, player.getUUID(), player.getScoreboardName(), 1);
                })
                .then(Commands.literal("page")
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(cs -> {
                            if (!cs.getSource().isPlayer()) {
                                cs.getSource().sendSuccess(() -> Component.literal("A player must run this command."), false);
                                return 0;
                            }
                            ServerPlayer player = cs.getSource().getPlayer();
                            return sendPaginatedStructures(cs, player.getUUID(), player.getScoreboardName(), IntegerArgumentType.getInteger(cs, "page"));
                        })
                    )
                )

                // /discoveries structure <structureId>
                .then(Commands.literal("structure")
                    .then(Commands.argument("structureId", StringArgumentType.string())
                        .executes(cs -> {
                            String structureIdStr = StringArgumentType.getString(cs, "structureId");
                            Identifier structureId = Identifier.tryParse(structureIdStr);
                            if (structureId == null) {
                                cs.getSource().sendSuccess(() -> Component.literal("Invalid structure ID: " + structureIdStr), false);
                                return 0;
                            }
                            return listPlayersForStructure(cs, structureId);
                        })
                    )
                )

                // /discoveries player <playername>
                .then(Commands.literal("player")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((cs, builder) -> {
                            PlayerNameCache.get(cs.getSource().getServer()).getAllNames()
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(cs -> {
                            String name = StringArgumentType.getString(cs, "player");
                            UUID uuid = PlayerNameCache.get(cs.getSource().getServer()).resolveUUID(name);
                            if (uuid == null) {
                                cs.getSource().sendFailure(Component.literal("Unknown player: " + name));
                                return 0;
                            }
                            return sendPaginatedStructures(cs, uuid, name, 1);
                        })
                        .then(Commands.literal("page")
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(cs -> {
                                    String name = StringArgumentType.getString(cs, "player");
                                    UUID uuid = PlayerNameCache.get(cs.getSource().getServer()).resolveUUID(name);
                                    if (uuid == null) {
                                        cs.getSource().sendFailure(Component.literal("Unknown player: " + name));
                                        return 0;
                                    }
                                    return sendPaginatedStructures(cs, uuid, name, IntegerArgumentType.getInteger(cs, "page"));
                                })
                            )
                        )
                    )
                )

                // /discoveries leaderboard
                .then(Commands.literal("leaderboard")
                    .executes(cs -> sendLeaderboard(cs, 1))
                    .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(cs -> sendLeaderboard(cs, IntegerArgumentType.getInteger(cs, "page")))
                        )
                    )
                )

                // /discoveries reload — OP only
                .then(Commands.literal("reload")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(cs -> {
                        reloadConfig.run();
                        cs.getSource().sendSuccess(() -> Component.literal("Structure Explorer config reloaded.").withStyle(ChatFormatting.GREEN), false);
                        return 1;
                    })
                )
        );

        dispatcher.register(Commands.literal("discoveries").redirect(visitsSource));
    }

    private static int listPlayersForStructure(CommandContext<CommandSourceStack> cs, Identifier structureId) {
        MinecraftServer server = cs.getSource().getServer();
        Set<UUID> players = PlayerStructureData.get(server).getPlayersForStructure(structureId);

        if (players.isEmpty()) {
            cs.getSource().sendSuccess(() -> Component.literal("No players recorded for " + structureId + "."), false);
            return 1;
        }

        MutableComponent component = Component.literal("Players who discovered ")
            .append(clickableStructure(structureId))
            .append(Component.literal(":"));

        PlayerNameCache nameCache = PlayerNameCache.get(server);
        for (UUID uuid : players) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            String name = online != null ? online.getScoreboardName() : nameCache.resolveName(uuid);
            component.append(Component.literal("\n - ").withStyle(ChatFormatting.RESET))
                     .append(Component.literal(name).withStyle(ChatFormatting.AQUA));
        }

        cs.getSource().sendSuccess(() -> component, false);
        return 1;
    }

    private static int getTotalStructureCount(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE).size();
    }

    private static int sendPaginatedStructures(CommandContext<CommandSourceStack> cs, UUID playerUuid, String playerName, int page) {
        MinecraftServer server = cs.getSource().getServer();

        List<Identifier> structures = new ArrayList<>(PlayerStructureData.get(server).getStructuresForPlayer(playerUuid));
        structures.sort(Comparator.comparing(Identifier::toString));

        if (structures.isEmpty()) {
            cs.getSource().sendSuccess(() -> Component.literal("No structures recorded for " + playerName + "."), false);
            return 1;
        }

        int totalItems = structures.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (page > totalPages || page < 1) {
            cs.getSource().sendFailure(Component.literal("Invalid page number. Total pages: " + totalPages));
            return 0;
        }

        boolean isSelf = cs.getSource().isPlayer() && cs.getSource().getPlayer().getUUID().equals(playerUuid);
        String baseCommandPrefix = isSelf ? "/discoveries page " : "/discoveries player " + playerName + " page ";

        // LINE 1: Header
        int total = getTotalStructureCount(server);
        MutableComponent header = Component.literal(totalItems + "/" + total + " Structures discovered by ")
            .append(Component.literal(playerName).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(":"));
        cs.getSource().sendSuccess(() -> header, false);

        // LINES 2-9: Content
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            Identifier structureId = structures.get(i);
            MutableComponent lineItem = Component.literal(" - ").withStyle(ChatFormatting.RESET)
                .append(clickableStructure(structureId));
            cs.getSource().sendSuccess(() -> lineItem, false);
        }

        // Pad remaining lines if last page has fewer than 8 entries
        int printedLines = endIndex - startIndex;
        for (int p = printedLines; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        // LINE 10: Footer
        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCommand = baseCommandPrefix + (page - 1);
            MutableComponent prevButton = Component.literal("[Previous] ")
                .withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand(prevCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))
                );
            footer.append(prevButton);
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCommand = baseCommandPrefix + (page + 1);
            MutableComponent nextButton = Component.literal("[Next]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))
                );
            footer.append(nextButton);
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    private static int sendLeaderboard(CommandContext<CommandSourceStack> cs, int page) {
        MinecraftServer server = cs.getSource().getServer();
        PlayerNameCache nameCache = PlayerNameCache.get(server);

        List<Map.Entry<UUID, Integer>> leaderboard = PlayerStructureData.get(server).getLeaderboard();

        if (leaderboard.isEmpty()) {
            cs.getSource().sendSuccess(() -> Component.literal("No data recorded yet."), false);
            return 1;
        }

        int totalItems = leaderboard.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (page > totalPages || page < 1) {
            cs.getSource().sendFailure(Component.literal("Invalid page number. Total pages: " + totalPages));
            return 0;
        }

        // LINE 1: Header
        MutableComponent header = Component.literal("Structure Discovery Leaderboard")
            .withStyle(ChatFormatting.GOLD);
        cs.getSource().sendSuccess(() -> header, false);

        // LINES 2-9: Content
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, Integer> entry = leaderboard.get(i);
            String name = nameCache.resolveName(entry.getKey());
            int count = entry.getValue();
            int rank = i + 1;
            MutableComponent line = Component.literal(" " + rank + ". ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(name).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD));
            cs.getSource().sendSuccess(() -> line, false);
        }

        // Pad remaining lines
        int printedLines = endIndex - startIndex;
        for (int p = printedLines; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        // LINE 10: Footer
        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCommand = "/discoveries leaderboard page " + (page - 1);
            footer.append(Component.literal("[Previous] ")
                .withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand(prevCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))
                ));
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCommand = "/discoveries leaderboard page " + (page + 1);
            footer.append(Component.literal("[Next]")
                .withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))
                ));
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    public static MutableComponent clickableStructure(Identifier structureId) {
        return Component.literal(structureId.toString())
            .withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/discoveries structure \"" + structureId + "\""))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to see all discoverers")))
            );
    }
}