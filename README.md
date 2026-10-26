# TWMTW Pillars 1.18.2 — source prototype

## Important
This is **source code**, **not a compiled Forge mod**. The generator is an independent visual approximation of the TWMTW/BWG sea pillars, not an exact port of BWG's source. It is **not game-tested**. The ZIP includes a modified FIX3 datapack that REQUIRES the compiled mod; do not install the datapack before the mod.

## Compilation
1. Install JDK 17 and Gradle 7.6 (or use a matching Forge MDK Gradle wrapper).
2. In this project folder, run `gradle build` with Internet access for ForgeGradle dependencies.
3. Copy `build/libs/twmtw-pillars-1.18.2-0.1.0.jar` to `mods/`.
4. Copy `TWMTW_Islands_Pillars_1.18.2_DATAPACK.zip` to a **new** test world's `datapacks/`, **replacing FIX3**.
5. Keep Forge Tectonic 2.3.5a and Regions Unexplored enabled.

## Notes / limits
- This prototype spawns stone / mossy-cobble / moss pillars at the ocean floor, using original size ranges (15–75 height; 7–20 radius) and feature rarity 1/5. It uses an independent approximation of the contour; no precise BWG parity is claimed.
- No Regions Unexplored blocks are needed yet. RU integration is reserved for further iteration so that unknown block IDs do not prevent world loading.
- Tectonic Islands noise/density functions and island size/frequency settings are not changed relative to FIX3.
- Requires **compilation and in-game testing**. API compatibility has not been verified against a Forge development environment. If Gradle reports errors, use its build output to fix the source.
- This procedural feature can be performance-intensive (large radius). Test on a separate world.

## Correction du 26 octobre 2026
Le type `RandomSource` (API plus récente) a été remplacé par `java.util.Random` pour cibler Minecraft 1.18.2. Projet toujours NON compilé et NON testé : ce système ne dispose pas de Gradle/ForgeGradle, et le téléchargement externe est bloqué.
