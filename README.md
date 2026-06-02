# Structure Explorer

**This mod adds incentive to players to try and discover all the structures there are in the world!**

I wanted to make a server that incentivized exploring all areas of the world, and with more structures being added every few months, but I couldn't find any mod that tracked the discovered structures. I tried making it for myself many moons ago and failed, but tried again and got it done in just two days.

It works by automatically checking every 5s (by default), any moving players, to see if they are within a structure, and adds that to a list of discovered structures for that player.

## ⌨ Commands

Primary command: `/explorer` — Alias: `/discoveries`

| Command | Description |
|---|---|
| `/explorer page? <n>` | Your discovered structures. Page is optional |
| `/explorer player <name> page? <n>` | View another player's discoveries. Page is optional |
| `/explorer structure <id>` | Who has discovered a specific structure |
| `/explorer instances <id> page? <n>` | Your recorded instances of a specific structure. Page is optional |
| `/explorer leaderboard page? <n>` | Top players ranked by discovery count. Page is optional |
| `/explorer playerinstances <player> <id>` | **[OP]** View any player's instances |
| `/explorer reload` | **[OP]** Reload config and translations |

---

## 📝 Config File — `structure_explorer.json`

Located at: `config/structure_explorer/structure_explorer.json`

| Key | Default | Description |
|---|---|---|
| `checkIntervalMs` | `5000` | How often (in ms) the mod checks if a player has entered a structure. Lower = more responsive, slightly more server load. |
| `showNthDiscoverer` | `true` | Whether to show discoverer badges in chat notifications, e.g. `[First Discoverer!]` or `[3rd Discoverer]`. |
| `useMonthDayYear` | `false` | Timestamp format. `false` = DD/MM/YYYY HH:MM:SS — `true` = MM/DD/YYYY HH:MM:SS |
| `trackInstances` | `true` | Whether to record coordinates and timestamps for each structure instance visited. When `false`, only which structure *types* have been discovered is tracked. |

---

## 🗣 Translations

Vanilla structure names are now properly named by default.  
e.g. `minecraft:fortress` ➡ **Nether Fortress**  
Methods of translating them from other mods can be done following the instructions below.  

## Translations Folder — `translations/`

Located at: `config/structure_explorer/translations/`

Place JSON files here to rename structures and apply prefix, suffix, and color styling to any namespace. Files are loaded **after** the mod's built-in translations, so they always override or extend them.

Reload at any time with `/explorer reload` (requires OP).

Files can be named anything — e.g. `minecraft.json`, `custom.json`.

---

## Translation File Format

```json
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
```

Here is an example, using the [Minecraft Vanilla Locations](https://github.com/KCB4Rockstar/Structure-Explorer-Fabric-Mod/blob/master/fabric/src/main/resources/data/structureexplorer/structure_explorer/translations.json)

---

## Key Reference

### `replace` *(optional, default: `false`)*

| Value | Behaviour |
|---|---|
| `false` | **Merge** — structure entries are unioned (new file wins on conflict), prefix/suffix/colors override only if non-empty, all other built-in names are kept as-is. |
| `true` | **Replace** — completely discards built-in translations for that namespace. Only what you define in this file will exist for that namespace. |

### `prefix` / `suffix` *(optional, default: `""`)*
Text shown before or after the structure name.
- e.g. `"prefix": "Minecraft: "` → **Minecraft: Stronghold**
- e.g. `"suffix": " [Katter's Structures]"` → **Armory [Katter's Structures]**

### `prefix_color` / `suffix_color` / `name_color` *(optional)*
Color applied to each part independently. If blank or omitted, defaults to **gold**. See [Colors](#colors) below.

### `structures` *(optional)*
A map of structure path → display name. The path is the part **after the colon** in the structure ID.

> `minecraft:stronghold` → path is `stronghold`

---

## Colors

**Named colors** *(case-insensitive)*:

| | | | |
|---|---|---|---|
| `black` | `dark_blue` | `dark_green` | `dark_aqua` |
| `dark_red` | `dark_purple` | `gold` | `gray` |
| `dark_gray` | `blue` | `green` | `aqua` |
| `red` | `light_purple` | `yellow` | `white` |

**Hex colors** *(CSS-style, prefix with `#`)*:
```
"#FF5500"   "#00AAFF"   "#FFFFFF"
```

---

## Examples

### Add a prefix to all Minecraft structures, keep all names
```json
{
  "translations": {
    "minecraft": {
      "prefix": "Minecraft: ",
      "prefix_color": "dark_green"
    }
  }
}
```
Result: **Minecraft: Stronghold**, **Minecraft: End City**, etc.

---

### Override specific names and add a custom entry, keep everything else
```json
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
```

---

### Replace all Minecraft translations — only show what you define
```json
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
```
All other `minecraft:` structures will fall back to showing their raw ID.

---

### Third-party mod with custom styling
```json
{
  "translations": {
    "kattersstructures": {
      "suffix": " [Katter's Structures]",
      "suffix_color": "gray",
      "structures": {
        "armory": "Armory"
      }
    }
  }
}
```
Result: **Armory [Katter's Structures]**

---

## 🛠 For Mod Makers

Mod makers can bundle translations inside their mod jar so they load automatically with zero setup for the user:

```
your-mod.jar
└── resources/
    └── data/
        └── <your_modid>/
            └── structure_explorer/
                └── translations.json
```

If anyone wants to make unofficial Datapacks for different mods to support Structure Explorer translations, you can use this format.

```
your-datapack
  └── data/
      └── <your_namespace>/
          └── structure_explorer/
              └── translations.json
```

Bundled mod translations load **before** `config/translations/` files, so server admins can always override them by placing their own file in the translations folder.

I couldn't have started making this mod without modifying and learning from the [WITS mod](https://github.com/TelepathicGrunt/WITS) by TelepathicGrunt
