package in.fellaguy.StructureExplorer.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.fellaguy.StructureExplorer.translations.NamespaceTranslation;
import in.fellaguy.StructureExplorer.translations.StructureTranslations;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TranslationLoader implements SimpleSynchronousResourceReloadListener {
    public static final Identifier ID = Identifier.tryParse("structureexplorer:structure_translations");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, NamespaceTranslation> merged = new HashMap<>();

        // 0. Auto-detect structure names from mod lang files (assets/<ns>/lang/en_us.json)
        loadFromModLangFiles(merged);

        // 1. Load from all mod jars and active datapacks
        // Files at data/<any_namespace>/structure_explorer/translations.json
        manager.listResources("structure_explorer",
            id -> id.getPath().equals("structure_explorer/translations.json")
        ).forEach((id, resource) -> {
            try (InputStream stream = resource.open();
                 InputStreamReader reader = new InputStreamReader(stream)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                StructureTranslations.parseAndMerge(json, merged);
            } catch (Exception ignored) {}
        });

        // 2. Load from config/structure_explorer/translations/*.json (overrides datapacks)
        Path transDir = FabricLoader.getInstance().getConfigDir()
            .resolve("structure_explorer")
            .resolve("translations");
        File dir = transDir.toFile();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (Reader reader = new FileReader(file)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        StructureTranslations.parseAndMerge(json, merged);
                    } catch (Exception ignored) {}
                }
            }
        }

        StructureTranslations.load(merged);
    }

    // Scans all loaded mod jars for assets/<namespace>/lang/en_us.json and extracts
    // keys matching "structure.<namespace>.<path>" as base-layer display names.
    private static void loadFromModLangFiles(Map<String, NamespaceTranslation> target) {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Optional<Path> assetsOpt = mod.findPath("assets");
            if (assetsOpt.isEmpty()) continue;

            try (var namespaceDirs = Files.list(assetsOpt.get())) {
                namespaceDirs.filter(Files::isDirectory).forEach(nsDir -> {
                    String namespace = nsDir.getFileName().toString();
                    Path langFile = nsDir.resolve("lang/en_us.json");
                    if (!Files.exists(langFile)) return;

                    Map<String, String> structures = new HashMap<>();
                    try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(langFile))) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            String key = entry.getKey();
                            // Only pick up keys in the form: structure.<namespace>.<path>
                            if (!key.startsWith("structure.") || !entry.getValue().isJsonPrimitive()) continue;
                            String[] parts = key.split("\\.", 3);
                            if (parts.length != 3 || !parts[1].equals(namespace)) continue;
                            structures.put(parts[2], entry.getValue().getAsString());
                        }
                    } catch (Exception ignored) {}

                    if (structures.isEmpty()) return;

                    // Merge as base layer — existing entries win (higher-priority sources loaded after)
                    NamespaceTranslation existing = target.get(namespace);
                    if (existing == null) {
                        target.put(namespace, new NamespaceTranslation("", "", null, null, null, structures));
                    } else {
                        Map<String, String> merged = new HashMap<>(structures);
                        merged.putAll(existing.structures); // existing wins over lang file
                        target.put(namespace, new NamespaceTranslation(
                            existing.prefix, existing.suffix,
                            existing.prefixColor, existing.suffixColor, existing.nameColor,
                            merged
                        ));
                    }
                });
            } catch (Exception ignored) {}
        }
    }
}
