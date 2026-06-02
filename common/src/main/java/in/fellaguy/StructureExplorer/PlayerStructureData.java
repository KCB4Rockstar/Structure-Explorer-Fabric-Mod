package in.fellaguy.StructureExplorer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerStructureData extends SavedData {

    public static final class StructureInstance {
        public final BlockPos origin;   // null for legacy records
        public final Instant timestamp; // null for legacy records

        public StructureInstance(BlockPos origin, Instant timestamp) {
            this.origin = origin;
            this.timestamp = timestamp;
        }

        public static final Codec<StructureInstance> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                BlockPos.CODEC.optionalFieldOf("origin")
                    .forGetter(i -> Optional.ofNullable(i.origin)),
                Codec.LONG.optionalFieldOf("timestamp")
                    .forGetter(i -> Optional.ofNullable(i.timestamp).map(Instant::toEpochMilli))
            ).apply(inst, (origin, ts) -> new StructureInstance(
                origin.orElse(null),
                ts.map(Instant::ofEpochMilli).orElse(null)
            ))
        );
    }

    // byPlayer: UUID -> (StructureID -> list of instances)
    private final Map<UUID, Map<Identifier, List<StructureInstance>>> byPlayer;
    // byStructure: StructureID -> set of player UUIDs (rebuilt on load, not saved directly)
    private final Map<Identifier, Set<UUID>> byStructure;

    public PlayerStructureData() {
        this.byPlayer = new HashMap<>();
        this.byStructure = new HashMap<>();
    }

    private PlayerStructureData(Map<UUID, Map<Identifier, List<StructureInstance>>> data) {
        this.byPlayer = new HashMap<>();
        this.byStructure = new HashMap<>();
        for (Map.Entry<UUID, Map<Identifier, List<StructureInstance>>> entry : data.entrySet()) {
            Map<Identifier, List<StructureInstance>> inner = new HashMap<>();
            for (Map.Entry<Identifier, List<StructureInstance>> e : entry.getValue().entrySet()) {
                inner.put(e.getKey(), new ArrayList<>(e.getValue()));
                byStructure.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(entry.getKey());
            }
            byPlayer.put(entry.getKey(), inner);
        }
    }

    // Migrates old Set<Identifier> format — each structure gets an empty instance list
    private static PlayerStructureData fromLegacy(Map<UUID, Set<Identifier>> legacy) {
        Map<UUID, Map<Identifier, List<StructureInstance>>> converted = new HashMap<>();
        for (Map.Entry<UUID, Set<Identifier>> entry : legacy.entrySet()) {
            Map<Identifier, List<StructureInstance>> inner = new HashMap<>();
            for (Identifier id : entry.getValue()) {
                inner.put(id, new ArrayList<>());
            }
            converted.put(entry.getKey(), inner);
        }
        return new PlayerStructureData(converted);
    }

    // --- Codecs ---

    private static final Codec<Map<Identifier, List<StructureInstance>>> INNER_CODEC =
        Codec.unboundedMap(Identifier.CODEC, StructureInstance.CODEC.listOf());

    private static final Codec<Map<UUID, Map<Identifier, List<StructureInstance>>>> NEW_MAP_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, INNER_CODEC);

    private static final Codec<Map<UUID, Set<Identifier>>> OLD_MAP_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC,
            Identifier.CODEC.listOf().xmap(HashSet::new, List::copyOf));

    // Tries new format first; falls back to old format and migrates transparently
    private static final Codec<PlayerStructureData> CODEC =
        Codec.either(NEW_MAP_CODEC, OLD_MAP_CODEC).xmap(
            either -> either.map(PlayerStructureData::new, PlayerStructureData::fromLegacy),
            data -> Either.left(data.byPlayer)
        );

    public static final SavedDataType<PlayerStructureData> TYPE = new SavedDataType<>(
        Identifier.tryParse("structureexplorer:player_structure_data"),
        PlayerStructureData::new,
        CODEC,
        null
    );

    public static PlayerStructureData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- Query methods ---

    public boolean hasDiscovered(UUID playerUuid, Identifier structureId) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
        return structures != null && structures.containsKey(structureId);
    }

    public boolean hasInstance(UUID playerUuid, Identifier structureId, BlockPos origin) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
        if (structures == null) return false;
        List<StructureInstance> instances = structures.get(structureId);
        if (instances == null) return false;
        return instances.stream().anyMatch(i -> i.origin != null && i.origin.equals(origin));
    }

    public Set<Identifier> getStructuresForPlayer(UUID playerUuid) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
        if (structures == null) return Collections.emptySet();
        return Collections.unmodifiableSet(structures.keySet());
    }

    public Set<UUID> getPlayersForStructure(Identifier structureId) {
        return Collections.unmodifiableSet(byStructure.getOrDefault(structureId, Collections.emptySet()));
    }

    public List<StructureInstance> getInstances(UUID playerUuid, Identifier structureId) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
        if (structures == null) return Collections.emptyList();
        return Collections.unmodifiableList(structures.getOrDefault(structureId, Collections.emptyList()));
    }

    public List<Map.Entry<UUID, Integer>> getLeaderboard() {
        return byPlayer.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), e.getValue().size()))
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
    }

    // --- Mutation methods ---

    // Creates the key only (no instance) — used when trackInstances is disabled
    public void addDiscoveryKey(UUID playerUuid, Identifier structureId) {
        byPlayer.computeIfAbsent(playerUuid, k -> new HashMap<>())
            .computeIfAbsent(structureId, k -> new ArrayList<>());
        byStructure.computeIfAbsent(structureId, k -> new HashSet<>()).add(playerUuid);
        setDirty();
    }

    // Creates the key and adds the first instance — triggers the discovery announcement in caller
    public void addDiscovery(UUID playerUuid, Identifier structureId, BlockPos origin, Instant timestamp) {
        byPlayer.computeIfAbsent(playerUuid, k -> new HashMap<>())
            .computeIfAbsent(structureId, k -> new ArrayList<>())
            .add(new StructureInstance(origin, timestamp));
        byStructure.computeIfAbsent(structureId, k -> new HashSet<>()).add(playerUuid);
        setDirty();
    }

    // Adds a new instance to an already-discovered structure — no announcement
    public void addInstance(UUID playerUuid, Identifier structureId, BlockPos origin, Instant timestamp) {
        byPlayer.computeIfAbsent(playerUuid, k -> new HashMap<>())
            .computeIfAbsent(structureId, k -> new ArrayList<>())
            .add(new StructureInstance(origin, timestamp));
        setDirty();
    }

    // Backfills a timestamp on a known instance that was recorded without one
    public void updateTimestamp(UUID playerUuid, Identifier structureId, BlockPos origin, Instant timestamp) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
        if (structures == null) return;
        List<StructureInstance> instances = structures.get(structureId);
        if (instances == null) return;
        for (int i = 0; i < instances.size(); i++) {
            StructureInstance inst = instances.get(i);
            if (inst.origin != null && inst.origin.equals(origin) && inst.timestamp == null) {
                instances.set(i, new StructureInstance(origin, timestamp));
                setDirty();
                return;
            }
        }
    }

    public void remove(UUID playerUuid, Identifier structureId) {
        Map<Identifier, List<StructureInstance>> structures = byPlayer.get(playerUuid);
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
}
