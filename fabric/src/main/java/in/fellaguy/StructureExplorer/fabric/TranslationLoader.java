package in.fellaguy.StructureExplorer.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.fellaguy.StructureExplorer.translations.NamespaceTranslation;
import in.fellaguy.StructureExplorer.translations.StructureTranslations;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TranslationLoader implements SimpleSynchronousResourceReloadListener {
    public static final Identifier ID = Identifier.tryParse("structureexplorer:structure_translations");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, NamespaceTranslation> merged = new HashMap<>();

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
}
