================================================================
  Structure Explorer - Configuration Guide
================================================================

COMMANDS
--------
Primary command : /explorer
Alias           : /discoveries

  /explorer                              - Your discovered structures (page 1)
  /explorer page <n>                     - Paginated view of your discoveries
  /explorer player <name>                - View another player's discoveries
  /explorer structure <id>               - Who has discovered a structure
  /explorer instances <id>               - Your instances of a specific structure
  /explorer here                         - Show info for the structure you're in
  /explorer leaderboard                  - Top players by discovery count
  /explorer playerinstances <p> <id>     - [OP] View any player's instances
  /explorer reload                       - [OP] Reload config and translations

================================================================
  CONFIG FILE: structure_explorer.json
================================================================

checkIntervalMs  (default: 5000)
    How often in milliseconds the mod checks if a player has entered
    a structure. Lower = more responsive, slightly more server load.

showNthDiscoverer  (default: true)
    Whether to show discoverer badges in chat notifications.
    e.g. [First Discoverer!] or [3rd Discoverer]

useMonthDayYear  (default: false)
    Timestamp format for structure instance records.
    false = DD/MM/YYYY HH:MM:SS
    true  = MM/DD/YYYY HH:MM:SS

trackInstances  (default: true)
    Whether to record coordinates and timestamps for each structure
    instance visited. When false, only which structure types have been
    discovered is tracked — no coordinates or timestamps are stored.

sounds
    Nested object containing sound toggles.

    newDiscoverySound  (default: true)
        Play a sound to the player when they discover a new structure.

    newInstanceSound  (default: true)
        Play a subtle sound when a new structure instance is recorded
        (i.e. revisiting a structure type at a new location).

================================================================
  TRANSLATIONS FOLDER: translations/
================================================================

Place JSON files here to rename structures and apply prefix/suffix/
color styling to any namespace. Files load after the mod's built-in
translations, so they override or extend them automatically.

Reload at any time with: /explorer reload  (requires OP)

Files can be named anything (e.g. minecraft.json, custom.json).

----------------------------------------------------------------
  FILE FORMAT
----------------------------------------------------------------

{
  "replace": false,
  "translations": {
    "namespace": {
      "prefix": "",
      "prefix_color": "",
      "suffix": "",
      "suffix_color": "",
      "name_color": "",
      "structures": {
        "structure_path": "Display Name"
      }
    }
  }
}

----------------------------------------------------------------
  KEY REFERENCE
----------------------------------------------------------------

replace  (optional, default: false)
    false - Merges with existing translations for that namespace:
            - structure entries are unioned (new wins on conflict)
            - prefix/suffix/colors override only if non-empty
            - all other built-in names are kept as-is
    true  - Completely replaces the namespace. Built-in translations
            for that namespace are discarded. Only what you define
            in this file will exist for that namespace.

prefix  (optional, default: "")
    Text shown before the structure name. e.g. "Minecraft: "

suffix  (optional, default: "")
    Text shown after the structure name. e.g. " [Katter's Structures]"

prefix_color / suffix_color / name_color  (optional)
    Color for each part. If blank or omitted, defaults to gold.
    See COLORS section below for accepted values.

structures  (optional)
    Map of structure path -> display name.
    The path is the part after the colon in the structure ID.
    e.g. for "minecraft:stronghold" the path is "stronghold"

----------------------------------------------------------------
  COLORS
----------------------------------------------------------------

Named colors (case-insensitive):
    black       dark_blue    dark_green   dark_aqua
    dark_red    dark_purple  gold         gray
    dark_gray   blue         green        aqua
    red         light_purple yellow       white

Hex colors (CSS-style, prefix with #):
    "#FF5500"  "#00AAFF"  "#FFFFFF"

----------------------------------------------------------------
  EXAMPLES
----------------------------------------------------------------

-- Add a green prefix to all Minecraft structures, keep all names --
{
  "translations": {
    "minecraft": {
      "prefix": "Minecraft: ",
      "prefix_color": "dark_green"
    }
  }
}

-- Override two names, add a custom entry, keep everything else --
{
  "translations": {
    "minecraft": {
      "structures": {
        "ocean_ruin_cold": "Cold Ocean Ruins",
        "ocean_ruin_warm": "Warm Ocean Ruins",
        "dungeon": "Mob Dungeon"
      }
    }
  }
}

-- Replace all Minecraft translations (only show what you define) --
{
  "replace": true,
  "translations": {
    "minecraft": {
      "structures": {
        "stronghold": "Stronghold",
        "end_city": "End City"
      }
    }
  }
}

-- Mod maker support --
Mod makers can bundle translations inside their mod jar at:
    resources/data/<modid>/structure_explorer/translations.json
They load automatically before config/translations/ files,
so server admins can always override them.

================================================================
