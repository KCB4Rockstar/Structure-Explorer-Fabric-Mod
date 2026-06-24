package in.fellaguy.StructureExplorer.translations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class StructureTranslations {
    private static final TextColor DEFAULT_COLOR = TextColor.fromRgb(0xFFAA00); // gold

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    static {
        NAMED_COLORS.put("black",        0x000000);
        NAMED_COLORS.put("dark_blue",    0x0000AA);
        NAMED_COLORS.put("dark_green",   0x00AA00);
        NAMED_COLORS.put("dark_aqua",    0x00AAAA);
        NAMED_COLORS.put("dark_red",     0xAA0000);
        NAMED_COLORS.put("dark_purple",  0xAA00AA);
        NAMED_COLORS.put("gold",         0xFFAA00);
        NAMED_COLORS.put("gray",         0xAAAAAA);
        NAMED_COLORS.put("dark_gray",    0x555555);
        NAMED_COLORS.put("blue",         0x5555FF);
        NAMED_COLORS.put("green",        0x55FF55);
        NAMED_COLORS.put("aqua",         0x55FFFF);
        NAMED_COLORS.put("red",          0xFF5555);
        NAMED_COLORS.put("light_purple", 0xFF55FF);
        NAMED_COLORS.put("yellow",       0xFFFF55);
        NAMED_COLORS.put("white",        0xFFFFFF);
    }
    private static Map<String, NamespaceTranslation> translations = new HashMap<>();
    // Bare names auto-detected from mod lang files: namespace -> (structure path -> display name)
    // Kept separate from `translations` so we know whether a name came purely from auto-detection
    // (gets the "[Namespace]" suffix) or is being styled/overridden by an explicit translation entry.
    private static Map<String, Map<String, String>> langNames = new HashMap<>();

    public static void load(Map<String, NamespaceTranslation> loadedTranslations, Map<String, Map<String, String>> loadedLangNames) {
        translations = Map.copyOf(loadedTranslations);
        langNames = Map.copyOf(loadedLangNames);
    }

    // Returns a styled component for the structure - no click/hover events (caller applies those)
    public static MutableComponent resolve(Identifier structureId) {
        String namespace = structureId.getNamespace();
        String path = structureId.getPath();

        NamespaceTranslation ns = translations.get(namespace);
        Map<String, String> nsLangNames = langNames.get(namespace);
        String langName = nsLangNames != null ? nsLangNames.get(path) : null;

        // No explicit translation entry for this namespace at all
        if (ns == null) {
            String readable = langName != null
                ? langName + " [" + prettify(namespace) + "]"
                : prettify(path) + " [" + prettify(namespace) + "]";
            return Component.literal(readable)
                .withStyle(style -> style.withColor(DEFAULT_COLOR));
        }

        // An explicit translation entry exists for this namespace - use the override name if present,
        // otherwise fall back to the lang-derived name, otherwise the raw path. No "[Namespace]" suffix.
        String name;
        if (ns.structures.containsKey(path)) {
            name = ns.structures.get(path);
        } else if (langName != null) {
            name = langName;
        } else {
            name = prettify(path);
        }

        TextColor nameColor   = ns.nameColor   != null ? ns.nameColor   : DEFAULT_COLOR;
        TextColor prefixColor = ns.prefixColor != null ? ns.prefixColor : DEFAULT_COLOR;
        TextColor suffixColor = ns.suffixColor != null ? ns.suffixColor : DEFAULT_COLOR;

        MutableComponent component = Component.empty();

        if (!ns.prefix.isEmpty()) {
            component.append(Component.literal(ns.prefix)
                .withStyle(style -> style.withColor(prefixColor)));
        }

        component.append(Component.literal(name)
            .withStyle(style -> style.withColor(nameColor)));

        if (!ns.suffix.isEmpty()) {
            component.append(Component.literal(ns.suffix)
                .withStyle(style -> style.withColor(suffixColor)));
        }

        return component;
    }

    // Parses a translations JSON file and merges (or replaces) into target.
    // replace:true at the file root causes each namespace to fully replace any existing entry.
    // replace:false (default): structures are merged (union, new wins on conflict),
    //   prefix/suffix/colors override only if defined in the new file.
    public static void parseAndMerge(JsonObject root, Map<String, NamespaceTranslation> target) {
        if (!root.has("translations")) return;
        boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
        JsonObject translationsObj = root.getAsJsonObject("translations");

        for (Map.Entry<String, JsonElement> nsEntry : translationsObj.entrySet()) {
            String namespace = nsEntry.getKey();
            if (!nsEntry.getValue().isJsonObject()) continue;
            JsonObject nsObj = nsEntry.getValue().getAsJsonObject();

            String prefix = getString(nsObj, "prefix", "");
            String suffix = getString(nsObj, "suffix", "");
            TextColor prefixColor = parseColor(getString(nsObj, "prefix_color", ""));
            TextColor suffixColor = parseColor(getString(nsObj, "suffix_color", ""));
            TextColor nameColor   = parseColor(getString(nsObj, "name_color",   ""));

            Map<String, String> structures = new HashMap<>();
            if (nsObj.has("structures") && nsObj.get("structures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> se : nsObj.getAsJsonObject("structures").entrySet()) {
                    if (se.getValue().isJsonPrimitive()) {
                        structures.put(se.getKey(), se.getValue().getAsString());
                    }
                }
            }

            if (!replace && target.containsKey(namespace)) {
                NamespaceTranslation existing = target.get(namespace);
                Map<String, String> mergedStructures = new HashMap<>(existing.structures);
                mergedStructures.putAll(structures);
                target.put(namespace, new NamespaceTranslation(
                    !prefix.isEmpty()   ? prefix       : existing.prefix,
                    !suffix.isEmpty()   ? suffix       : existing.suffix,
                    prefixColor != null ? prefixColor  : existing.prefixColor,
                    suffixColor != null ? suffixColor  : existing.suffixColor,
                    nameColor   != null ? nameColor    : existing.nameColor,
                    mergedStructures
                ));
            } else {
                target.put(namespace, new NamespaceTranslation(
                    prefix, suffix, prefixColor, suffixColor, nameColor, structures));
            }
        }
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }

    // Replaces _ and / with spaces, then capitalises the first letter of every word.
    // e.g. "the_bumblezone" → "The Bumblezone", "village/plains" → "Village Plains"
    public static String prettify(String s) {
        String[] words = s.replace("_", " ").replace("/", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    public static TextColor parseColor(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.startsWith("#")) {
            try {
                return TextColor.fromRgb(Integer.parseInt(s.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Integer rgb = NAMED_COLORS.get(s.toLowerCase(Locale.ROOT));
        if (rgb != null) return TextColor.fromRgb(rgb);
        return null;
    }
}
