# Last Pagans Campaign Map

Forge 1.20.1 mod providing an authoritative raster province system and fullscreen political campaign map.

## Use

- Press **M** to open the campaign map.
- Left-click a province to select it.
- Use the mouse wheel to zoom and right-drag to pan.
- Press **P** inside the map to toggle the political tint layer.
- Press **Esc** to close the map.

The visible terrain comes from `campaign_front.png`. Province identity always comes from the hidden exact-colour `province_key.png`; the frontend image is never sampled for geography.

## Projection and map configuration

Edit `src/main/resources/assets/campaignmap/campaign/campaign_map.json`, then rebuild. `worldMinX`, `worldMaxX`, `worldMinZ`, and `worldMaxZ` define the inclusive Minecraft-coordinate rectangle represented by the raster. `flipX` and `flipZ` explicitly control orientation. All world/map conversions go through `MapProjection`.

The included `-200..199` bounds are demonstration defaults copied from the project brief. They are **not known to match the intended Minecraft world**. Set them to the actual WorldPainter/world bounds and set each flip only after comparing known landmarks. Raster resolution and world size are independent.

Each province entry has a stable resource ID, a mutable display name, and an exact RGB identifier:

```json
{"id": "campaignmap:province_0", "name": "Treeia", "rgb": [0, 127, 14]}
```

Black is reserved as unassigned by `unassignedRgb`, unless a future configuration deliberately changes that value. Startup validation rejects duplicate IDs, duplicate colours, undefined raster colours, missing province pixels, missing images, and mismatched image dimensions.

## Commands

- `/campaignmap list`
- `/campaignmap where <x> <z>` — reports normalized coordinates, pixel, exact RGB, and province
- `/campaignmap info <province>`
- `/campaignmap setowner <province> <owner> <RRGGBB>`
- `/campaignmap clearowner <province>`
- `/campaignmap setcontroller <province> <controller> <RRGGBB>`
- `/campaignmap clearcontroller <province>`

Mutation commands require permission level 2. Ownership and control are world SavedData, survive restarts, and synchronize from the server on join and after changes.

## External mod API

Use the dedicated-server-safe classes under `com.lastpagans.campaignmap.api`:

```java
CampaignMapAPI.getProvinceAt(level, blockPos);
CampaignMapAPI.getProvinceAt(level, x, z);
CampaignMapAPI.getProvince(provinceId);
CampaignMapAPI.getOwner(serverLevel, provinceId);
CampaignMapAPI.getController(serverLevel, provinceId);
CampaignMapAPI.getNeighbours(provinceId);
```

Geography is immutable map data. Political ownership and military control are separate dynamic data. External mods do not need to depend on GUI or texture classes.

## Build

Use Java 17 or a Gradle toolchain capable of obtaining Java 17:

```sh
./gradlew clean test build
```

The distributable JAR is written to `build/libs/campaignmap-0.1.0.jar`.
