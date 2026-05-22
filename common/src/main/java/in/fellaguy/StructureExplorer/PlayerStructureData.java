package in.fellaguy.StructureExplorer;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.stream.Collectors;

import java.util.*;

public class PlayerStructureData extends SavedData {

    private final Map<UUID, Set<Identifier>> byPlayer;
    private final Map<Identifier, Set<UUID>> byStructure;

    public PlayerStructureData() {
        this.byPlayer = new HashMap<>();
        this.byStructure = new HashMap<>();
    }

    // Reconstructs both indexes from the saved byPlayer map
    private PlayerStructureData(Map<UUID, Set<Identifier>> byPlayer) {
        this.byPlayer = new HashMap<>();
        this.byStructure = new HashMap<>();

        for (Map.Entry<UUID, Set<Identifier>> entry : byPlayer.entrySet()) {
            for (Identifier structureId : entry.getValue()) {
                addToIndexes(entry.getKey(), structureId);
            }
        }
    }

    private void addToIndexes(UUID playerUuid, Identifier structureId) {
        byPlayer.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(structureId);
        byStructure.computeIfAbsent(structureId, k -> new HashSet<>()).add(playerUuid);
    }

    private static final Codec<Map<UUID, Set<Identifier>>> MAP_CODEC =
        Codec.unboundedMap(
            UUIDUtil.STRING_CODEC,
            Identifier.CODEC.listOf().xmap(HashSet::new, List::copyOf)
        );

    private static final Codec<PlayerStructureData> CODEC =
        MAP_CODEC.xmap(
            PlayerStructureData::new,
            h -> h.byPlayer
        );

    public static final SavedDataType<PlayerStructureData> TYPE = new SavedDataType<>(
        "player_structure_data",
        PlayerStructureData::new,
        CODEC,
        null
    );

    public static PlayerStructureData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // Add a pair — if it already exists, this is a no-op
    public void add(UUID playerUuid, Identifier structureId) {
        addToIndexes(playerUuid, structureId);
        setDirty();
    }

    // Remove a specific pair
    public void remove(UUID playerUuid, Identifier structureId) {
        Set<Identifier> structures = byPlayer.get(playerUuid);
        if (structures != null) {
            structures.remove(structureId);
            if (structures.isEmpty()) byPlayer.remove(playerUuid);
        }

        Set<UUID> players = byStructure.get(structureId);
        if (players != null) {
            players.remove(playerUuid);
            if (players.isEmpty()) byStructure.remove(structureId);
        }

        setDirty();
    }

    // Get all structures for a player
    public Set<Identifier> getStructuresForPlayer(UUID playerUuid) {
        return Collections.unmodifiableSet(byPlayer.getOrDefault(playerUuid, Collections.emptySet()));
    }

    // Get all players for a structure
    public Set<UUID> getPlayersForStructure(Identifier structureId) {
        return Collections.unmodifiableSet(byStructure.getOrDefault(structureId, Collections.emptySet()));
    }

    // Returns all players sorted by structure count descending
    public List<Map.Entry<UUID, Integer>> getLeaderboard() {
        return byPlayer.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), e.getValue().size()))
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .collect(java.util.stream.Collectors.toList());
    }
}