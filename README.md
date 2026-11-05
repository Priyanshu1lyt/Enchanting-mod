# Enchantment Customizer (Fabric, Minecraft 1.21.11)

Standalone Fabric mod: press **K** (rebindable) to open a GUI with a 3D player preview, an inventory item selector and an enchantment panel.

## Build
- Java 21 required.
- GitHub Actions (`.github/workflows/build.yml`) builds the JAR; download it from the Actions run artifacts.
- Local: install Gradle 9.x, then `gradle build`. JAR is in `build/libs/`.

## Install
Put the JAR (plus Fabric API) in `mods/`. For multiplayer the mod must be installed on the **server too** (the server applies and validates every change).

## Status
Source written against Yarn `1.21.11+build.4`, Fabric API `0.141.3+1.21.11`. **Not yet compiled or tested in-game** - see the notes in the chat/PR for known limitations.
