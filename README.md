# Blood FX

A **client-side** Fabric mod. When a player takes damage you see blood, and every kind of sword hit looks and sounds different:

| Hit | How it is detected | Blood | Tint colour (default) |
|---|---|---|---|
| Normal damage | anything else | small splash | red |
| Knockback | sprint hit | stream thrown away from the attacker | orange |
| Crit | falling hit | big burst | yellow |
| Sweep | sweeping edge | wide fan | purple |

- **Hit colour tint:** the player who was hit is tinted in the colour of the hit instead of vanilla's red flash, and it lasts **twice as long** (20 ticks instead of 10) by default.
- **Sounds:** every hit type has the vanilla sound plus **4 extra sounds** to choose from (or keep the original), each with its own volume.
- **Everything is configurable in Mod Menu** (*Mods → Blood FX → config*): particles on/off and amount, sounds on/off and volumes, tint on/off, tint duration, tint brightness, and the colour and sound of every hit type.
- **Remote updates:** the mod can check this repository's GitHub Releases and update itself (see below).
- Players only (not mobs).

The mod only needs to be installed on **your** client. It reads the hit type from the vanilla attack sounds the server already sends, so it works on any server.

Settings are stored in `config/bloodfx.json`.

## Remote updates

In Mod Menu (*General* tab) you can set **Updates** to:

- **OFF**: never checks.
- **NOTIFY**: tells you in chat when a newer release exists; press *Install the update* to download it.
- **AUTO** (default): downloads the new jar automatically.

The update is downloaded only from this repository's GitHub Releases, for your exact Minecraft version, and is checked (size limit, SHA-256 from GitHub when available, and that the jar really is Blood FX at the announced version) before it is used. The new jar waits in `config/bloodfx/update/` and replaces the old one **when you close the game**, so restart once to get it. If the swap ever fails, the old jar is kept.

Requirements: the repository (or at least its releases) must be **public**, and releases must be published by pushing a tag like `v1.0.1` (the GitHub Actions workflow attaches one jar per Minecraft version). Some mod sites (Modrinth, CurseForge) may not allow self-updating mods, so set the default to NOTIFY or OFF in `BloodFxConfig.updateMode` before publishing there.

## Supported versions

Fabric, Minecraft **1.21.11**, **26.1**, **26.2** and **26.3**. Each version has its own jar (see the Releases page).

## Building

Needs JDK 25.

```bash
./gradlew -p mc-1.21.11 build   # or mc-26.1, mc-26.2, mc-26.3
```

The jar ends up in `mc-<version>/build/libs/`. Pushing to GitHub builds all four versions with GitHub Actions (`.github/workflows/build.yml`); pushing a tag like `v1.0.0` also publishes a release with the jars.

## Project layout

```
shared/        code and assets used by every version (logic, config, GUI, sounds)
compat-1.21/   the few classes that differ in 1.21.x
compat-26/     the few classes that differ in 26.x
mc-<version>/  one independent Gradle build per Minecraft version
gradle/        shared build script and the wrapper
```

Fabric API and Fabric Loader versions are looked up automatically while building. Mod Menu has one release line per Minecraft version, so the matching one is pinned in `gradle/common.gradle` (`modMenuByMinecraft`). To override any of them, set `fabric_api_version`, `loader_version` or `modmenu_version` in that version's `gradle.properties`. `loom_version` in the same file is the Fabric Loom version and is the first thing to change if a plugin can't be resolved.

## Sounds

The 12 extra sounds (`sweep_1-4`, `crit_1-4`, `knockback_1-4`) were synthesised from scratch with a script, so there is nothing third-party in them. To use your own, replace the `.ogg` files in `shared/resources/assets/bloodfx/sounds/` (mono, Ogg Vorbis).

## License

MIT
