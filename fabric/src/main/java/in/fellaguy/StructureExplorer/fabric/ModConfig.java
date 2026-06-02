package in.fellaguy.StructureExplorer.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public long checkIntervalMs = 5000;
    public boolean showNthDiscoverer = true;
    public boolean useMonthDayYear = false;
    public boolean trackInstances = true;

    private static ModConfig instance = new ModConfig();

    public static ModConfig get() {
        return instance;
    }

    public static void load(Path configDir) {
        Path configFile = configDir.resolve("structure_explorer.json");
        File file = configFile.toFile();

        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException e) {
                instance = new ModConfig();
            }
            // Write back so any fields added in newer versions appear in the file
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(instance, writer);
            } catch (IOException ignored) {}
        } else {
            instance = new ModConfig();
            try {
                file.getParentFile().mkdirs();
                try (Writer writer = new FileWriter(file)) {
                    GSON.toJson(instance, writer);
                }
            } catch (IOException ignored) {}
        }
    }

    // 1 tick = 50ms at 20 TPS
    public int getCheckIntervalTicks() {
        return (int) Math.max(1, checkIntervalMs / 50);
    }
}
