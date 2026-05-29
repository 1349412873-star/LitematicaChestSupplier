# Litematica Chest Supplier

A Fabric client mod for Minecraft that helps collect and use materials for the current Litematica schematic.

## Features

- Scans the currently loaded Litematica schematic for missing blocks.
- Uses Chest Tracker records to find chests containing needed items.
- Highlights matching chests in the world.
- Can automatically open highlighted chests when you look at them.
- Automatically moves only the materials still needed by the schematic.
- Highlights nearby missing block positions for items already in your inventory.
- Shows Chinese or English chat messages based on the current Minecraft language.

## Requirements

- Minecraft 1.21.8
- Fabric Loader 0.18.4 or newer
- Fabric API
- MaLiLib
- Litematica
- Chest Tracker
- Java 21

## Controls

- `O`: Toggle auto-supply.
- `P`: Toggle missing block highlight.

## Commands

- `/lcs toggle`: Toggle auto-supply.
- `/lcs status`: Show current missing schematic materials.
- `/lcs highlight`: Toggle missing block highlight.
- `/lcs reload`: Reload the config file.

## Build

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The built mod jar is generated in `build/libs/`.

## Config

The config file is created at:

```text
.minecraft/config/litematicachestsupplier.json
```

Main options:

- `enabled`: whether auto-supply is enabled.
- `supplyCooldown`: delay between inventory actions, in ticks.
- `maxDistance`: maximum distance for auto-opening a chest.
- `autoOpen`: whether matching chests should open automatically.
- `highlightDistance`: missing block highlight render distance.
- `highlightMaxCount`: maximum number of missing block outlines to render.
