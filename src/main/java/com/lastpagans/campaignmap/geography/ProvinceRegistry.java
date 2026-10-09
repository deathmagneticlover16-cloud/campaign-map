package com.lastpagans.campaignmap.geography;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.ProvinceDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProvinceRegistry {
    public static final String CONFIG_RESOURCE = "/assets/campaignmap/campaign/campaign_map.json";
    public static final String RASTER_RESOURCE = "/assets/campaignmap/campaign/province_key.png";
    public static final String FRONTEND_RESOURCE = "/assets/campaignmap/campaign/campaign_front.png";
    private static volatile ProvinceRegistry instance;

    private final Map<Integer, ProvinceDefinition> byRgb;
    private final Map<ResourceLocation, ProvinceDefinition> byId;
    private final Map<ResourceLocation, Set<ResourceLocation>> adjacency;
    private final Map<ResourceLocation, MapProjection.PixelPoint> labelPoints;
    private final ProvinceRaster raster;
    private final MapProjection projection;
    private final int unassignedRgb;

    private ProvinceRegistry(Map<Integer, ProvinceDefinition> byRgb,
                             Map<ResourceLocation, ProvinceDefinition> byId,
                             Map<ResourceLocation, Set<ResourceLocation>> adjacency,
                             Map<ResourceLocation, MapProjection.PixelPoint> labelPoints,
                             ProvinceRaster raster, MapProjection projection, int unassignedRgb) {
        this.byRgb = Map.copyOf(byRgb);
        this.byId = Map.copyOf(byId);
        Map<ResourceLocation, Set<ResourceLocation>> immutableAdjacency = new HashMap<>();
        adjacency.forEach((id, neighbours) -> immutableAdjacency.put(id, Set.copyOf(neighbours)));
        this.adjacency = Map.copyOf(immutableAdjacency);
        this.labelPoints = Map.copyOf(labelPoints);
        this.raster = raster;
        this.projection = projection;
        this.unassignedRgb = unassignedRgb;
    }

    public static ProvinceRegistry get() {
        ProvinceRegistry current = instance;
        if (current == null) {
            synchronized (ProvinceRegistry.class) {
                current = instance;
                if (current == null) instance = current = loadBundled();
            }
        }
        return current;
    }

    public static synchronized void reloadBundled() {
        instance = loadBundled();
    }

    private static ProvinceRegistry loadBundled() {
        try (InputStream configStream = requiredResource(CONFIG_RESOURCE);
             InputStream rasterStream = requiredResource(RASTER_RESOURCE);
             InputStream frontendStream = requiredResource(FRONTEND_RESOURCE)) {
            Config config = new Gson().fromJson(new InputStreamReader(configStream, StandardCharsets.UTF_8), Config.class);
            if (config == null || config.projection == null || config.provinces == null) {
                throw new JsonParseException("campaign_map.json is missing projection or provinces");
            }
            ProvinceRaster raster = ProvinceRaster.read(rasterStream);
            BufferedImage frontend = ImageIO.read(frontendStream);
            if (frontend == null || frontend.getWidth() != raster.width() || frontend.getHeight() != raster.height()) {
                throw new IllegalStateException("Frontend map and province raster dimensions must match");
            }
            Map<Integer, ProvinceDefinition> byRgb = new HashMap<>();
            Map<ResourceLocation, ProvinceDefinition> byId = new LinkedHashMap<>();
            for (ProvinceJson entry : config.provinces) {
                ResourceLocation id = ResourceLocation.tryParse(entry.id);
                if (id == null || entry.name == null || entry.rgb == null || entry.rgb.length != 3) {
                    throw new JsonParseException("Invalid province definition: " + entry.id);
                }
                int rgb = rgb(entry.rgb);
                ProvinceDefinition definition = new ProvinceDefinition(id, entry.name, rgb);
                if (byId.putIfAbsent(id, definition) != null) throw new JsonParseException("Duplicate province id " + id);
                if (byRgb.putIfAbsent(rgb, definition) != null) throw new JsonParseException("Duplicate province RGB " + definition.rgbHex());
            }
            int unassigned = config.unassignedRgb == null ? 0 : rgb(config.unassignedRgb);
            Map<ResourceLocation, long[]> sums = new HashMap<>();
            for (ProvinceDefinition definition : byId.values()) sums.put(definition.id(), new long[3]);
            Set<Integer> unknown = new TreeSet<>();
            Map<ResourceLocation, Set<ResourceLocation>> adjacency = new HashMap<>();
            byId.keySet().forEach(id -> adjacency.put(id, new HashSet<>()));
            for (int y = 0; y < raster.height(); y++) {
                for (int x = 0; x < raster.width(); x++) {
                    int color = raster.rgbAt(x, y);
                    ProvinceDefinition province = byRgb.get(color);
                    if (province == null) {
                        if (color != unassigned) unknown.add(color);
                        continue;
                    }
                    long[] sum = sums.get(province.id());
                    sum[0] += x; sum[1] += y; sum[2]++;
                    if (x + 1 < raster.width()) connect(byRgb.get(raster.rgbAt(x + 1, y)), province, adjacency);
                    if (y + 1 < raster.height()) connect(byRgb.get(raster.rgbAt(x, y + 1)), province, adjacency);
                }
            }
            if (!unknown.isEmpty()) throw new IllegalStateException("Province raster has undefined RGB values: " + unknown);
            Map<ResourceLocation, MapProjection.PixelPoint> labels = new HashMap<>();
            for (ProvinceDefinition province : byId.values()) {
                long[] sum = sums.get(province.id());
                if (sum[2] == 0) throw new IllegalStateException("Province has no pixels: " + province.id());
                int centerX = (int) (sum[0] / sum[2]);
                int centerY = (int) (sum[1] / sum[2]);
                labels.put(province.id(), nearestPixel(raster, province.rgb(), centerX, centerY));
            }
            ProjectionJson p = config.projection;
            MapProjection projection = new MapProjection(p.worldMinX, p.worldMaxX, p.worldMinZ, p.worldMaxZ,
                    p.flipX, p.flipZ, raster.width(), raster.height());
            CampaignMapMod.LOGGER.info("Loaded {} provinces from {}x{} campaign raster", byId.size(), raster.width(), raster.height());
            return new ProvinceRegistry(byRgb, byId, adjacency, labels, raster, projection, unassigned);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load bundled campaign map", exception);
        }
    }

    private static InputStream requiredResource(String name) throws IOException {
        InputStream stream = ProvinceRegistry.class.getResourceAsStream(name);
        if (stream == null) throw new IOException("Missing resource " + name);
        return stream;
    }

    private static int rgb(int[] components) {
        if (components.length != 3) throw new JsonParseException("RGB must have exactly three components");
        for (int component : components) if (component < 0 || component > 255) throw new JsonParseException("RGB component out of range");
        return (components[0] << 16) | (components[1] << 8) | components[2];
    }

    private static void connect(@Nullable ProvinceDefinition a, ProvinceDefinition b,
                                Map<ResourceLocation, Set<ResourceLocation>> adjacency) {
        if (a != null && !a.id().equals(b.id())) {
            adjacency.get(a.id()).add(b.id());
            adjacency.get(b.id()).add(a.id());
        }
    }

    private static MapProjection.PixelPoint nearestPixel(ProvinceRaster raster, int rgb, int centerX, int centerY) {
        if (raster.rgbAt(centerX, centerY) == rgb) return new MapProjection.PixelPoint(centerX, centerY);
        int maxRadius = Math.max(raster.width(), raster.height());
        for (int radius = 1; radius < maxRadius; radius++) {
            for (int y = Math.max(0, centerY - radius); y <= Math.min(raster.height() - 1, centerY + radius); y++) {
                for (int x = Math.max(0, centerX - radius); x <= Math.min(raster.width() - 1, centerX + radius); x++) {
                    if ((Math.abs(x - centerX) == radius || Math.abs(y - centerY) == radius) && raster.rgbAt(x, y) == rgb) {
                        return new MapProjection.PixelPoint(x, y);
                    }
                }
            }
        }
        throw new IllegalStateException("Could not find a label pixel");
    }

    @Nullable public ProvinceDefinition atPixel(int x, int y) {
        if (!projection.isPixelInsideMap(x, y)) return null;
        return byRgb.get(raster.rgbAt(x, y));
    }

    @Nullable public ProvinceDefinition atWorld(double x, double z) {
        return projection.worldToPixel(x, z).map(point -> atPixel(point.x(), point.y())).orElse(null);
    }

    @Nullable public ProvinceDefinition byId(ResourceLocation id) { return byId.get(id); }
    public Collection<ProvinceDefinition> provinces() { return byId.values(); }
    public Set<ResourceLocation> neighbours(ResourceLocation id) { return adjacency.getOrDefault(id, Set.of()); }
    @Nullable public MapProjection.PixelPoint labelPoint(ResourceLocation id) { return labelPoints.get(id); }
    public ProvinceRaster raster() { return raster; }
    public MapProjection projection() { return projection; }
    public int unassignedRgb() { return unassignedRgb; }

    private static final class Config {
        ProjectionJson projection;
        int[] unassignedRgb;
        List<ProvinceJson> provinces;
    }
    private static final class ProjectionJson {
        double worldMinX;
        double worldMaxX;
        double worldMinZ;
        double worldMaxZ;
        boolean flipX;
        boolean flipZ;
    }
    private static final class ProvinceJson {
        String id;
        String name;
        int[] rgb;
    }
}
