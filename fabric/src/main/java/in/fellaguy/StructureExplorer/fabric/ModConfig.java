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
        Path modDir = configDir.resolve("structure_explorer");
        Path configFile = modDir.resolve("structure_explorer.json");
        File file = configFile.toFile();

        // Always ensure the folder structure exists
        modDir.resolve("translations").toFile().mkdirs();
        writeReadme(modDir.resolve("readme.txt").toFile());

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
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(instance, writer);
            } catch (IOException ignored) {}
        }
    }

    // 1 tick = 50ms at 20 TPS
    public int getCheckIntervalTicks() {
        return (int) Math.max(1, checkIntervalMs / 50);
    }

    private static void writeReadme(File file) {
        if (file.exists()) return;
        String content =
            "================================================================\n" +
            "  Structure Explorer - Configuration Guide\n" +
            "================================================================\n" +
            "\n" +
            "COMMANDS\n" +
            "--------\n" +
            "Primary command : /explorer\n" +
            "Alias           : /discoveries\n" +
            "\n" +
            "  /explorer                              - Your discovered structures (page 1)\n" +
            "  /explorer page <n>                     - Paginated view of your discoveries\n" +
            "  /explorer player <name>                - View another player's discoveries\n" +
            "  /explorer structure <id>               - Who has discovered a structure\n" +
            "  /explorer instances <id>               - Your instances of a specific structure\n" +
            "  /explorer leaderboard                  - Top players by discovery count\n" +
            "  /explorer playerinstances <p> <id>     - [OP] View any player's instances\n" +
            "  /explorer reload                       - [OP] Reload config and translations\n" +
            "\n" +
            "================================================================\n" +
            "  CONFIG FILE: structure_explorer.json\n" +
            "================================================================\n" +
            "\n" +
            "checkIntervalMs  (default: 5000)\n" +
            "    How often in milliseconds the mod checks if a player has entered\n" +
            "    a structure. Lower = more responsive, slightly more server load.\n" +
            "\n" +
            "showNthDiscoverer  (default: true)\n" +
            "    Whether to show discoverer badges in chat notifications.\n" +
            "    e.g. [First Discoverer!] or [3rd Discoverer]\n" +
            "\n" +
            "useMonthDayYear  (default: false)\n" +
            "    Timestamp format for structure instance records.\n" +
            "    false = DD/MM/YYYY HH:MM:SS\n" +
            "    true  = MM/DD/YYYY HH:MM:SS\n" +
            "\n" +
            "trackInstances  (default: true)\n" +
            "    Whether to record coordinates and timestamps for each structure\n" +
            "    instance visited. When false, only which structure types have been\n" +
            "    discovered is tracked — no coordinates or timestamps are stored.\n" +
            "\n" +
            "================================================================\n" +
            "  TRANSLATIONS FOLDER: translations/\n" +
            "================================================================\n" +
            "\n" +
            "Place JSON files here to rename structures and apply prefix/suffix/\n" +
            "color styling to any namespace. Files load after the mod's built-in\n" +
            "translations, so they override or extend them automatically.\n" +
            "\n" +
            "Reload at any time with: /explorer reload  (requires OP)\n" +
            "\n" +
            "Files can be named anything (e.g. minecraft.json, custom.json).\n" +
            "\n" +
            "----------------------------------------------------------------\n" +
            "  FILE FORMAT\n" +
            "----------------------------------------------------------------\n" +
            "\n" +
            "{\n" +
            "  \"replace\": false,\n" +
            "  \"translations\": {\n" +
            "    \"namespace\": {\n" +
            "      \"prefix\": \"\",\n" +
            "      \"prefix_color\": \"\",\n" +
            "      \"suffix\": \"\",\n" +
            "      \"suffix_color\": \"\",\n" +
            "      \"name_color\": \"\",\n" +
            "      \"structures\": {\n" +
            "        \"structure_path\": \"Display Name\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "----------------------------------------------------------------\n" +
            "  KEY REFERENCE\n" +
            "----------------------------------------------------------------\n" +
            "\n" +
            "replace  (optional, default: false)\n" +
            "    false - Merges with existing translations for that namespace:\n" +
            "            - structure entries are unioned (new wins on conflict)\n" +
            "            - prefix/suffix/colors override only if non-empty\n" +
            "            - all other built-in names are kept as-is\n" +
            "    true  - Completely replaces the namespace. Built-in translations\n" +
            "            for that namespace are discarded. Only what you define\n" +
            "            in this file will exist for that namespace.\n" +
            "\n" +
            "prefix  (optional, default: \"\")\n" +
            "    Text shown before the structure name. e.g. \"Minecraft: \"\n" +
            "\n" +
            "suffix  (optional, default: \"\")\n" +
            "    Text shown after the structure name. e.g. \" [Katter's Structures]\"\n" +
            "\n" +
            "prefix_color / suffix_color / name_color  (optional)\n" +
            "    Color for each part. If blank or omitted, defaults to gold.\n" +
            "    See COLORS section below for accepted values.\n" +
            "\n" +
            "structures  (optional)\n" +
            "    Map of structure path -> display name.\n" +
            "    The path is the part after the colon in the structure ID.\n" +
            "    e.g. for \"minecraft:stronghold\" the path is \"stronghold\"\n" +
            "\n" +
            "----------------------------------------------------------------\n" +
            "  COLORS\n" +
            "----------------------------------------------------------------\n" +
            "\n" +
            "Named colors (case-insensitive):\n" +
            "    black       dark_blue    dark_green   dark_aqua\n" +
            "    dark_red    dark_purple  gold         gray\n" +
            "    dark_gray   blue         green        aqua\n" +
            "    red         light_purple yellow       white\n" +
            "\n" +
            "Hex colors (CSS-style, prefix with #):\n" +
            "    \"#FF5500\"  \"#00AAFF\"  \"#FFFFFF\"\n" +
            "\n" +
            "----------------------------------------------------------------\n" +
            "  EXAMPLES\n" +
            "----------------------------------------------------------------\n" +
            "\n" +
            "-- Add a green prefix to all Minecraft structures, keep all names --\n" +
            "{\n" +
            "  \"translations\": {\n" +
            "    \"minecraft\": {\n" +
            "      \"prefix\": \"Minecraft: \",\n" +
            "      \"prefix_color\": \"dark_green\"\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "-- Override two names, add a custom entry, keep everything else --\n" +
            "{\n" +
            "  \"translations\": {\n" +
            "    \"minecraft\": {\n" +
            "      \"structures\": {\n" +
            "        \"ocean_ruin_cold\": \"Cold Ocean Ruins\",\n" +
            "        \"ocean_ruin_warm\": \"Warm Ocean Ruins\",\n" +
            "        \"dungeon\": \"Mob Dungeon\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "-- Replace all Minecraft translations (only show what you define) --\n" +
            "{\n" +
            "  \"replace\": true,\n" +
            "  \"translations\": {\n" +
            "    \"minecraft\": {\n" +
            "      \"structures\": {\n" +
            "        \"stronghold\": \"Stronghold\",\n" +
            "        \"end_city\": \"End City\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "-- Mod maker support --\n" +
            "Mod makers can bundle translations inside their mod jar at:\n" +
            "    resources/data/<modid>/structure_explorer/translations.json\n" +
            "They load automatically before config/translations/ files,\n" +
            "so server admins can always override them.\n" +
            "\n" +
            "================================================================\n";

        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        } catch (IOException ignored) {}
    }
}
