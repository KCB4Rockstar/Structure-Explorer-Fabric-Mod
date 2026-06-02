package in.fellaguy.StructureExplorer.translations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class StructureTranslations {
    private static final TextColor DEFAULT_COLOR = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
    private static Map<String, NamespaceTranslation> translations = new HashMap<>();

    public static void load(Map<String, NamespaceTranslation> loaded) {
        translations = Map.copyOf(loaded);
    }

    // Returns a styled component for the structure — no click/hover events (caller applies those)
    public static MutableComponent resolve(Identifier structureId) {
        NamespaceTranslation ns = translations.get(structureId.getNamespace());

        if (ns == null || !ns.structures.containsKey(structureId.getPath())) {
            return Component.literal(structureId.toString())
                .withStyle(style -> style.withColor(DEFAULT_COLOR));
        }

        String name = ns.structures.get(structureId.getPath());
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

    public static TextColor parseColor(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.startsWith("#")) {
            try {
                return TextColor.fromRgb(Integer.parseInt(s.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        ChatFormatting fmt = ChatFormatting.getByName(s.toUpperCase());
        if (fmt != null && fmt.isColor()) return TextColor.fromLegacyFormat(fmt);
        return null;
    }
}
