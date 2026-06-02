package in.fellaguy.StructureExplorer.translations;

import net.minecraft.network.chat.TextColor;

import java.util.Map;

public class NamespaceTranslation {
    public final String prefix;
    public final String suffix;
    public final TextColor prefixColor;  // null = use default (GOLD)
    public final TextColor suffixColor;
    public final TextColor nameColor;
    public final Map<String, String> structures; // structure path -> display name

    public NamespaceTranslation(String prefix, String suffix,
                                TextColor prefixColor, TextColor suffixColor, TextColor nameColor,
                                Map<String, String> structures) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.prefixColor = prefixColor;
        this.suffixColor = suffixColor;
        this.nameColor = nameColor;
        this.structures = Map.copyOf(structures);
    }
}
