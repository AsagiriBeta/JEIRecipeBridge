[![](http://cf.way2muchnoise.eu/versions/1427559.svg)](https://www.curseforge.com/minecraft/bukkit-plugins/jei-recipe-bridge)

# JEI Recipe Bridge

Paper plugin that sends server recipes to Fabric / NeoForge clients so JEI can display them.

Since Minecraft 1.21.2+, recipes live on the server only. This plugin bridges them to modded clients — no config, no commands, drop in and go.

## Install

1. Download the JAR from [GitHub Releases](https://github.com/AsagiriBeta/JEIRecipeBridge/releases) or [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/jei-recipe-bridge).
2. Put it in your **Paper** server `plugins/` folder.
3. Restart.

Install on the **Paper backend only**, not on Velocity/BungeeCord.

## What it does

- Syncs recipes to **Fabric** and **NeoForge** clients on join
- Filters invalid recipes (e.g. air ingredients) to prevent client crashes
- Sends `fabric:recipe_sync_finished` for newer Fabric API
- Unlocks all server recipes via `discoverRecipes()` on join
- Re-syncs after datapack reload and ItemsAdder `/iareload`

## Compatibility

- Single JAR for Paper / Purpur / Folia **1.21.2 – 26.1.x**
- Java **21+**
- Runtime reflection for NMS — no per-version builds

## License

MIT © 2026 Mrbysco
