# Architecture

`ProvincePixelMap` holds the exact 24-bit RGB pixels from the hidden province-key PNG. It never reads the frontend art and provides constant-time pixel lookup.

`MapProjection` owns every conversion between Minecraft X/Z and province-image pixel X/Y. Bounds are inclusive, normalized coordinates are independent of image resolution, maximum-edge pixels are clamped safely, and each axis flips only when configured.

`ProvinceRegistry` loads and validates bundled configuration and images. It maps RGB to `ProvinceDefinition`, maps stable IDs to definitions, derives adjacency by scanning touching pixels, and chooses valid interior label pixels. `getProvinceAt` follows world coordinate → projection → image pixel → RGB → definition.

`CampaignOwnershipData` is overworld `SavedData`. It maps stable province IDs to separate owner and controller identities. Geography never changes when politics changes.

`CampaignMapAPI` is the small public, server-safe integration layer. Future Settlements and Recruits integrations can query province, owner, controller, and neighbours without importing client code.

`CampaignNetwork` sends a full ownership snapshot when a player joins and a single province update after an admin mutation. Clients cannot send ownership changes. `ClientOwnershipCache` holds the synchronized rendering copy.

`CampaignMapScreen` renders the frontend terrain, cached political tint, derived province/national borders, labels, hover/selection highlight, and information panel. Its dynamic overlay rebuilds only when synchronized politics or hover/selection changes. Mouse selection uses screen → map-local → image-pixel coordinates and remains correct while panning and zooming.

`CampaignArmyData` owns strategic army position and movement. Every army has one real Recruits commander UUID. The commander is selected and validated before army creation, packed as the same persistent entity for strategic movement, and restored during deployment. `CommanderVisualSnapshot` contains only the commander entity type and visible appearance/equipment fields needed by clients.

`MountedCommanderRenderer` creates render-only client proxies which are never added to the level. It routes the commander through the Recruits entity renderer and uses the exact `icys-better-horses:mustang_horse` entity and Icy renderer for every mount. Simulated 20 Hz movement drives Icy's ordinary walk state. The hoof origin stays anchored to the projected map point, the pair remains upright, and world heading is transformed against campaign-camera yaw instead of billboarding toward the viewer. Neither proxy can run AI or influence authoritative movement.

`ClientArmyCache` uses velocity-continuous cubic interpolation between authoritative position updates. Each new segment inherits the previous visual velocity and estimates its ending velocity from consecutive server positions, avoiding the repeated stop/start motion produced by easing toward discrete packet targets.

The current resources are bundled for a self-contained prototype. A future version can move definitions and province-image loading behind a datapack/resource reload layer without changing API consumers or ownership save data.
