package in.fellaguy.StructureExplorer.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import in.fellaguy.StructureExplorer.PlayerStructureData;
import in.fellaguy.StructureExplorer.PlayerStructureData.StructureInstance;
import in.fellaguy.StructureExplorer.PlayerNameCache;
import in.fellaguy.StructureExplorer.StructureExplorer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public class SECommand {
    private static final int ITEMS_PER_PAGE = 8;

    public static void createCommand(CommandDispatcher<CommandSourceStack> dispatcher, Runnable reloadConfig) {
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

                // /discoveries structure <structureId> — list all players who found it
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

                // /discoveries player <name>
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

                // /discoveries info <structureId> — shows options panel (triggered by clicking a structure name)
                .then(Commands.literal("info")
                    .then(Commands.argument("structureId", StringArgumentType.string())
                        .executes(cs -> {
                            String str = StringArgumentType.getString(cs, "structureId");
                            Identifier structureId = Identifier.tryParse(str);
                            if (structureId == null) {
                                cs.getSource().sendFailure(Component.literal("Invalid structure ID: " + str));
                                return 0;
                            }
                            return showStructureInfo(cs, structureId);
                        })
                    )
                )

                // /discoveries playerinstances <player> <structureId> [page <n>] — OP: view any player's instances
                .then(Commands.literal("playerinstances")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((cs, builder) -> {
                            PlayerNameCache.get(cs.getSource().getServer()).getAllNames()
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("structureId", StringArgumentType.string())
                            .executes(cs -> {
                                String name = StringArgumentType.getString(cs, "player");
                                UUID uuid = PlayerNameCache.get(cs.getSource().getServer()).resolveUUID(name);
                                if (uuid == null) {
                                    cs.getSource().sendFailure(Component.literal("Unknown player: " + name));
                                    return 0;
                                }
                                String str = StringArgumentType.getString(cs, "structureId");
                                Identifier structureId = Identifier.tryParse(str);
                                if (structureId == null) return 0;
                                return showAdminInstances(cs, uuid, name, structureId, 1);
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
                                        String str = StringArgumentType.getString(cs, "structureId");
                                        Identifier structureId = Identifier.tryParse(str);
                                        if (structureId == null) return 0;
                                        return showAdminInstances(cs, uuid, name, structureId, IntegerArgumentType.getInteger(cs, "page"));
                                    })
                                )
                            )
                        )
                    )
                )

                // /discoveries instances <structureId> [page <n>] — paginated instance list for calling player
                .then(Commands.literal("instances")
                    .then(Commands.argument("structureId", StringArgumentType.string())
                        .executes(cs -> {
                            if (!cs.getSource().isPlayer()) {
                                cs.getSource().sendSuccess(() -> Component.literal("A player must run this command."), false);
                                return 0;
                            }
                            String str = StringArgumentType.getString(cs, "structureId");
                            Identifier structureId = Identifier.tryParse(str);
                            if (structureId == null) return 0;
                            return showInstances(cs, structureId, 1);
                        })
                        .then(Commands.literal("page")
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(cs -> {
                                    if (!cs.getSource().isPlayer()) {
                                        cs.getSource().sendSuccess(() -> Component.literal("A player must run this command."), false);
                                        return 0;
                                    }
                                    String str = StringArgumentType.getString(cs, "structureId");
                                    Identifier structureId = Identifier.tryParse(str);
                                    if (structureId == null) return 0;
                                    return showInstances(cs, structureId, IntegerArgumentType.getInteger(cs, "page"));
                                })
                            )
                        )
                    )
                )
        );

        dispatcher.register(Commands.literal("discoveries").redirect(visitsSource));
    }

    // --- Info panel ---

    private static int showStructureInfo(CommandContext<CommandSourceStack> cs, Identifier structureId) {
        MutableComponent line1 = Component.literal(structureId.toString())
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(" | OPTIONS").withStyle(ChatFormatting.GRAY));
        cs.getSource().sendSuccess(() -> line1, false);

        MutableComponent showPlayers = Component.literal("[Show Players]")
            .withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/discoveries structure \"" + structureId + "\""))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Show all players who discovered this structure")))
            );

        MutableComponent showInstances = Component.literal(" [Show Instances]")
            .withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/discoveries instances \"" + structureId + "\""))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Show your instances of this structure")))
            );

        cs.getSource().sendSuccess(() -> Component.empty().append(showPlayers).append(showInstances), false);
        return 1;
    }

    // --- Instance list ---

    private static int showInstances(CommandContext<CommandSourceStack> cs, Identifier structureId, int page) {
        ServerPlayer player = cs.getSource().getPlayer();
        MinecraftServer server = cs.getSource().getServer();
        PlayerStructureData data = PlayerStructureData.get(server);

        boolean hasDiscovered = data.hasDiscovered(player.getUUID(), structureId);
        List<StructureInstance> instances = new ArrayList<>(data.getInstances(player.getUUID(), structureId));

        String playerName = player.getScoreboardName();
        int totalInstances = instances.size();
        String instanceWord = totalInstances == 1 ? "Instance" : "Instances";

        MutableComponent header = Component.literal("").
            append(Component.literal(String.valueOf(totalInstances)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" " + instanceWord + " of "))
            .append(Component.literal(structureId.toString()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" found by "))
            .append(Component.literal(playerName).withStyle(ChatFormatting.AQUA));
        cs.getSource().sendSuccess(() -> header, false);

        if (!hasDiscovered) {
            cs.getSource().sendSuccess(() -> Component.literal("You have not discovered this structure yet.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        if (totalInstances == 0) {
            String noInstanceMsg = StructureExplorer.trackInstances
                ? "No instances recorded. Revisit this structure to log it."
                : "Instance tracking is not enabled.";
            cs.getSource().sendSuccess(() -> Component.literal(noInstanceMsg).withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int totalPages = (int) Math.ceil((double) totalInstances / ITEMS_PER_PAGE);
        if (page > totalPages || page < 1) {
            cs.getSource().sendFailure(Component.literal("Invalid page number. Total pages: " + totalPages));
            return 0;
        }

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalInstances);

        boolean isOp = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(cs.getSource());
        for (int i = startIndex; i < endIndex; i++) {
            StructureInstance inst = instances.get(i);
            cs.getSource().sendSuccess(() -> instanceLine(inst, isOp), false);
        }

        int printed = endIndex - startIndex;
        for (int p = printed; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        String encodedId = "\"" + structureId + "\"";
        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCmd = "/discoveries instances " + encodedId + " page " + (page - 1);
            footer.append(Component.literal("[Previous] ").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(prevCmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))));
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCmd = "/discoveries instances " + encodedId + " page " + (page + 1);
            footer.append(Component.literal("[Next]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(nextCmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))));
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    // Builds a single instance line. adminMode=true makes coords clickable (suggest /tp)
    private static MutableComponent instanceLine(StructureInstance inst, boolean adminMode) {
        MutableComponent line = Component.empty();

        if (inst.timestamp != null) {
            line.append(Component.literal(formatTimestamp(inst.timestamp)).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            line.append(Component.literal("No date stored, revisit to add a new date.").withStyle(ChatFormatting.GRAY));
        }

        if (inst.origin != null) {
            int x = inst.origin.getX(), y = inst.origin.getY(), z = inst.origin.getZ();
            String coordText = " [" + x + " " + y + " " + z + "]";
            MutableComponent coords = Component.literal(coordText).withStyle(ChatFormatting.GREEN);
            if (adminMode) {
                String tpCmd = "/tp " + x + " " + y + " " + z;
                coords = coords.withStyle(style -> style
                    .withClickEvent(new ClickEvent.SuggestCommand(tpCmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(tpCmd)))
                );
            }
            line.append(coords);
        }

        return line;
    }

    private static int showAdminInstances(CommandContext<CommandSourceStack> cs, UUID targetUuid, String targetName, Identifier structureId, int page) {
        MinecraftServer server = cs.getSource().getServer();
        PlayerStructureData data = PlayerStructureData.get(server);

        boolean hasDiscovered = data.hasDiscovered(targetUuid, structureId);
        List<StructureInstance> instances = new ArrayList<>(data.getInstances(targetUuid, structureId));
        int totalInstances = instances.size();
        String instanceWord = totalInstances == 1 ? "Instance" : "Instances";

        MutableComponent header = Component.literal("")
            .append(Component.literal(String.valueOf(totalInstances)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" " + instanceWord + " of "))
            .append(Component.literal(structureId.toString()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" found by "))
            .append(Component.literal(targetName).withStyle(ChatFormatting.AQUA));
        cs.getSource().sendSuccess(() -> header, false);

        if (!hasDiscovered) {
            cs.getSource().sendSuccess(() -> Component.literal("This player has not discovered this structure.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        if (totalInstances == 0) {
            String noInstanceMsg = StructureExplorer.trackInstances
                ? "No instances recorded. Player must revisit to log them."
                : "Instance tracking is not enabled.";
            cs.getSource().sendSuccess(() -> Component.literal(noInstanceMsg).withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int totalPages = (int) Math.ceil((double) totalInstances / ITEMS_PER_PAGE);
        if (page > totalPages || page < 1) {
            cs.getSource().sendFailure(Component.literal("Invalid page number. Total pages: " + totalPages));
            return 0;
        }

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalInstances);

        for (int i = startIndex; i < endIndex; i++) {
            StructureInstance inst = instances.get(i);
            cs.getSource().sendSuccess(() -> instanceLine(inst, true), false);
        }

        int printed = endIndex - startIndex;
        for (int p = printed; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        String encodedId = "\"" + structureId + "\"";
        String encodedPlayer = "\"" + targetName + "\"";
        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCmd = "/discoveries playerinstances " + encodedPlayer + " " + encodedId + " page " + (page - 1);
            footer.append(Component.literal("[Previous] ").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(prevCmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))));
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCmd = "/discoveries playerinstances " + encodedPlayer + " " + encodedId + " page " + (page + 1);
            footer.append(Component.literal("[Next]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(nextCmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))));
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    private static String formatTimestamp(Instant timestamp) {
        LocalDateTime dt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
        String pattern = StructureExplorer.useMonthDayYear ? "MM/dd/yyyy HH:mm:ss" : "dd/MM/yyyy HH:mm:ss";
        return dt.format(DateTimeFormatter.ofPattern(pattern));
    }

    // --- Players for structure ---

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

    // --- Paginated structure list ---

    private static int getTotalStructureCount(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).size();
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
        boolean isOp = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(cs.getSource());
        String baseCommandPrefix = isSelf ? "/discoveries page " : "/discoveries player " + playerName + " page ";

        int total = getTotalStructureCount(server);
        MutableComponent header = Component.literal("")
            .append(Component.literal(totalItems + "/" + total).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" Structures discovered by "))
            .append(Component.literal(playerName).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(":"));
        cs.getSource().sendSuccess(() -> header, false);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            Identifier structureId = structures.get(i);
            MutableComponent lineItem = Component.literal(" - ").withStyle(ChatFormatting.RESET)
                .append(clickableStructure(structureId));
            if (isOp) {
                String cmd = "/discoveries playerinstances \"" + playerName + "\" \"" + structureId + "\"";
                lineItem.append(Component.literal(" [Show Instances]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("View " + playerName + "'s instances of this structure")))
                    )
                );
            }
            cs.getSource().sendSuccess(() -> lineItem, false);
        }

        int printedLines = endIndex - startIndex;
        for (int p = printedLines; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCommand = baseCommandPrefix + (page - 1);
            footer.append(Component.literal("[Previous] ").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(prevCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))));
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCommand = baseCommandPrefix + (page + 1);
            footer.append(Component.literal("[Next]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))));
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    // --- Leaderboard ---

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

        cs.getSource().sendSuccess(() -> Component.literal("Structure Discovery Leaderboard").withStyle(ChatFormatting.GOLD), false);

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

        int printedLines = endIndex - startIndex;
        for (int p = printedLines; p < ITEMS_PER_PAGE; p++) {
            cs.getSource().sendSuccess(() -> Component.literal(""), false);
        }

        MutableComponent footer = Component.empty();

        if (page > 1) {
            String prevCommand = "/discoveries leaderboard page " + (page - 1);
            footer.append(Component.literal("[Previous] ").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(prevCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))));
        } else {
            footer.append(Component.literal("[Previous] ").withStyle(ChatFormatting.GRAY));
        }

        footer.append(Component.literal("Page " + page + "/" + totalPages + " ").withStyle(ChatFormatting.WHITE));

        if (page < totalPages) {
            String nextCommand = "/discoveries leaderboard page " + (page + 1);
            footer.append(Component.literal("[Next]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))));
        } else {
            footer.append(Component.literal("[Next]").withStyle(ChatFormatting.GRAY));
        }

        cs.getSource().sendSuccess(() -> footer, false);
        return 1;
    }

    // --- Shared ---

    // Clicking a structure name opens the info/options panel
    public static MutableComponent clickableStructure(Identifier structureId) {
        return Component.literal(structureId.toString())
            .withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/discoveries info \"" + structureId + "\""))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click for options")))
            );
    }
}
