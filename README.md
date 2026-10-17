# Dark Rifts (NeoForge 1.21.1)

Tear rifts in the sky with the **Rift Blade** and step into **12 randomly chosen dark dimensions**
(eternal midnight, near-black skies and fog, dark water, ash/spore particles).

## Build & run
Requires JDK 21.

```
gradle wrapper          # once, if there is no ./gradlew yet
./gradlew runClient     # test in dev
./gradlew build         # jar -> build/libs/darkrifts-1.0.0.jar
```
If Gradle can't resolve the plugin/NeoForge version, bump `net.neoforged.moddev` in `build.gradle`
and `neo_version` in `gradle.properties` to the latest 21.1.x.

Regenerate all JSON/PNG resources (optional): `python3 tools/generate_assets.py` (needs Pillow).

## How to play
* **Rift Blade** recipe: 3 Echo Shards + Netherite Sword + Eye of Ender (`" E " / "ESE" / " P "`).
  Also in the creative tab "Dark Rifts".
* **Hold Right Click**, look at the sky and **drag your view** down / sideways / in any direction.
  The path your crosshair traces is drawn with particles; **release** to open the rift.
* Walk into a rift -> you arrive in a random dark dimension, next to a permanent **return rift**
  that takes you back to where you came from. Rifts inside dark dimensions lead to other dark dimensions.
* Natural rifts (ragged vertical tears) also generate in the overworld and inside the dark dimensions.
* Rifts you draw collapse after 5 minutes (config: `riftLifetimeSeconds`, 0 = permanent).

## Commands (op level 2)
`/darkrifts open` - permanent rift in front of you  |  `/darkrifts visit <1-12>`  |  `/darkrifts home`

## Config (`darkrifts-common.toml`)
`riftLifetimeSeconds`, `strokeRadius` (how far from you the line is drawn), `maxRiftBlocks`.

## Notes
* The 12 dimensions are different combinations of vanilla terrain presets (overworld, amplified, large biomes,
  caves, floating islands, end, nether) x 5 custom dark biomes. Vanilla noise generators share the world seed,
  so the variety comes from preset + biome mix, not from separate seeds.
* Adding more dimensions: edit `DIMENSIONS` in `tools/generate_assets.py` and `DarkDimensions.COUNT`.
