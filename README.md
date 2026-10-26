# Last Pagans Campaign Map

Forge 1.20.1 mod providing an authoritative province system, a smooth 3D campaign view, and persistent campaign armies backed by Recruits groups.

## Use

- Press **M** to open the campaign map.
- Left-click a province to select it.
- Use the mouse wheel to zoom, right-drag to pan, and middle-drag to angle the camera.
- Select an army banner and right-click the map to issue a server-authoritative movement order.
- Press **A** to open army management and create an army from your available Recruits groups.
- Press **P** inside the map to toggle the political tint layer.
- Press **Esc** to close the map.

The visible terrain comes from `campaign_front.png`. Province identity always comes from the hidden exact-colour `province_key.png`; the frontend image is never sampled for geography.

## Projection and map configuration

Edit `src/main/resources/assets/campaignmap/campaign/campaign_map.json`, then rebuild. `worldMinX`, `worldMaxX`, `worldMinZ`, and `worldMaxZ` define the inclusive Minecraft-coordinate rectangle represented by the province image. `flipX` and `flipZ` explicitly control orientation. All world/map conversions go through `MapProjection`.

The included `-200..199` bounds are demonstration defaults copied from the project brief. They are **not known to match the intended Minecraft world**. Set them to the actual WorldPainter/world bounds and set each flip only after comparing known landmarks. Image resolution and world size are independent.

Each province entry has a stable resource ID, a mutable display name, and an exact RGB identifier:

```json
{"id": "campaignmap:province_0", "name": "Treeia", "rgb": [0, 127, 14]}
```

Black is reserved as unassigned by `unassignedRgb`, unless a future configuration deliberately changes that value. Startup validation rejects duplicate IDs, duplicate colours, undefined pixel colours, missing province pixels, missing images, and mismatched image dimensions.

## Commands

- `/campaignmap list`
- `/campaignmap where <x> <z>` — reports normalized coordinates, pixel, exact RGB, and province
- `/campaignmap info <province>`
- `/campaignmap setowner <province> <owner> <RRGGBB>`
- `/campaignmap clearowner <province>`
- `/campaignmap setcontroller <province> <controller> <RRGGBB>`
- `/campaignmap clearcontroller <province>`

Mutation commands require permission level 2. Ownership and control are world SavedData, survive restarts, and synchronize from the server on join and after changes.

Army commands are available under `/campaignmap army`: `list`, `create`, `info`, `rename`, `addgroup`, `removegroup`, `move`, and `delete`. Army records have their own UUIDs and store only campaign state and Recruits group UUID references. Recruits remains the authority for groups and soldiers, and one group can belong to only one campaign army.

## External mod API

Use the dedicated-server-safe classes under `com.lastpagans.campaignmap.api`:

```java
CampaignMapAPI.getProvinceAt(level, blockPos);
CampaignMapAPI.getProvinceAt(level, x, z);
CampaignMapAPI.getProvince(provinceId);
CampaignMapAPI.getOwner(serverLevel, provinceId);
CampaignMapAPI.getController(serverLevel, provinceId);
CampaignMapAPI.getNeighbours(provinceId);

CampaignArmyAPI.getArmy(serverLevel, armyId);
CampaignArmyAPI.getArmies(serverLevel);
CampaignArmyAPI.getArmyForGroup(serverLevel, groupId);
```

Geography is immutable map data. Political ownership and military control are separate dynamic data. External mods do not need to depend on GUI or texture classes.

The Recruits dependency is optional at runtime. When Recruits is present, the adapter reads its persistent groups directly for owner checks, names, and soldier totals. The bundled `libs/recruits.jar` is compile-only and is not packaged into Campaign Map.

## Build

Use Java 17 or a Gradle toolchain capable of obtaining Java 17:

```sh
./gradlew clean test build
```

The distributable JAR is written to `build/libs/campaignmap-0.1.0.jar`.
