package in.fellaguy.StructureExplorer;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class PlayerNameCache extends SavedData {

    private final Map<UUID, String> uuidToName;

    public PlayerNameCache() {
        this.uuidToName = new HashMap<>();
    }

    private PlayerNameCache(Map<UUID, String> map) {
        this.uuidToName = new HashMap<>(map);
    }

    private static final Codec<Map<UUID, String>> MAP_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING);

    private static final Codec<PlayerNameCache> CODEC =
        MAP_CODEC.xmap(PlayerNameCache::new, c -> c.uuidToName);

    public static final SavedDataType<PlayerNameCache> TYPE = new SavedDataType<>(
        Identifier.tryParse("structureexplorer:player_name_cache"),
        PlayerNameCache::new,
        CODEC,
        null
    );

    public static PlayerNameCache get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    // Call this on join and on any command interaction
    public void update(UUID uuid, String name) {
        uuidToName.put(uuid, name);
        setDirty();
    }

    // Returns the player's name, or their UUID string as fallback
    public String resolveName(UUID uuid) {
        return uuidToName.getOrDefault(uuid, uuid.toString());
    }

    // Returns all known player names for tab-complete suggestions
    public Collection<String> getAllNames() {
        return Collections.unmodifiableCollection(uuidToName.values());
    }

    // Looks up a UUID by player name, case-insensitive
    public UUID resolveUUID(String name) {
        for (Map.Entry<UUID, String> entry : uuidToName.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }
}