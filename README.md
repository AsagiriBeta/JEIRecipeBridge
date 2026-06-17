[![](http://cf.way2muchnoise.eu/versions/1427559.svg)](https://www.curseforge.com/minecraft/bukkit-plugins/jei-recipe-bridge)

# JEI Recipe Bridge #

## About ##
This plugin sends server recipes to connecting clients in a format expected by Fabric / NeoForge clients, allowing JEI to display them.

Since Minecraft 1.21.2+, recipes are stored on the server only, which prevents JEI from showing recipes when playing on vanilla-based servers.

## Installation ##

This is a **Paper / Purpur / Folia backend plugin**. It is **not** a Velocity, BungeeCord, or Waterfall proxy plugin.

1. Download the JAR from [GitHub Releases](https://github.com/AsagiriBeta/JEIRecipeBridge/releases) or [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/jei-recipe-bridge).
2. Place it in your **game server** `plugins/` folder (the Paper world server players actually join).
3. Restart the Paper server (or use a plugin manager to load it).
4. Use `/jeibridge info` in-game or on the **Paper console** to verify the bridge is `ready`.

If you use a proxy (Velocity, BungeeCord, etc.), install this plugin **only on the Paper backend**. Commands and permissions apply on the backend world, not on the proxy.

## Compatibility ##

- **Single JAR** for Paper / Purpur / Folia **1.21.2 through 26.1.x**
- Built with Java **21** bytecode (runs on Java 21+ servers)
- Uses **runtime reflection** for NMS internals — no per-version plugin builds
- Supports **Fabric** and **NeoForge** clients with JEI / REI
- Filters recipes with invalid ingredients (e.g. air) to prevent client crashes
- Optional **ItemsAdder** integration (server-side only)

## License ##
* JEI Recipe Bridge licensed under the MIT license
  - (c) 2026 Mrbysco
  - [![License](https://img.shields.io/badge/License-MIT-red.svg?style=flat)](http://opensource.org/licenses/MIT)

## Downloads ##
Downloads will be available on [Curseforge](https://www.curseforge.com/minecraft/bukkit-plugins/jei-recipe-bridge)