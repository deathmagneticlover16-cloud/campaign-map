# Architecture

`ProvinceRaster` holds the exact 24-bit RGB pixels from the hidden province-key PNG. It never reads the frontend art and provides constant-time pixel lookup.

`MapProjection` owns every conversion between Minecraft X/Z and raster X/Y. Bounds are inclusive, normalized coordinates are independent of image resolution, maximum-edge pixels are clamped safely, and each axis flips only when configured.

`ProvinceRegistry` loads and validates bundled configuration and images. It maps RGB to `ProvinceDefinition`, maps stable IDs to definitions, derives adjacency by scanning touching pixels, and chooses valid interior label pixels. `getProvinceAt` follows world coordinate → projection → raster RGB → definition.

`CampaignOwnershipData` is overworld `SavedData`. It maps stable province IDs to separate owner and controller identities. Geography never changes when politics changes.

`CampaignMapAPI` is the small public, server-safe integration layer. Future Settlements and Recruits integrations can query province, owner, controller, and neighbours without importing client code.

`CampaignNetwork` sends a full ownership snapshot when a player joins and a single province update after an admin mutation. Clients cannot send ownership changes. `ClientOwnershipCache` holds the synchronized rendering copy.

`CampaignMapScreen` renders the frontend terrain, cached political tint, derived province/national borders, labels, hover/selection highlight, and information panel. Its dynamic overlay rebuilds only when synchronized politics or hover/selection changes. Mouse selection uses screen → map-local → raster coordinates and remains correct while panning and zooming.

The current resources are bundled for a self-contained prototype. A future version can move definitions and raster loading behind a datapack/resource reload layer without changing API consumers or ownership save data.
