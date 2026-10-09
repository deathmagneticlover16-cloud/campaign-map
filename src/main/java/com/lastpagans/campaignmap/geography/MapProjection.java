package com.lastpagans.campaignmap.geography;

import java.util.Optional;

/** The single conversion point between Minecraft X/Z and raster X/Y. */
public final class MapProjection {
    private final double worldMinX;
    private final double worldMaxX;
    private final double worldMinZ;
    private final double worldMaxZ;
    private final boolean flipX;
    private final boolean flipZ;
    private final int imageWidth;
    private final int imageHeight;

    public MapProjection(double worldMinX, double worldMaxX, double worldMinZ, double worldMaxZ,
                         boolean flipX, boolean flipZ, int imageWidth, int imageHeight) {
        if (!(worldMaxX > worldMinX) || !(worldMaxZ > worldMinZ)) {
            throw new IllegalArgumentException("World maximums must be greater than minimums");
        }
        if (imageWidth < 1 || imageHeight < 1) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
        this.worldMinX = worldMinX;
        this.worldMaxX = worldMaxX;
        this.worldMinZ = worldMinZ;
        this.worldMaxZ = worldMaxZ;
        this.flipX = flipX;
        this.flipZ = flipZ;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public Optional<PixelPoint> worldToPixel(double worldX, double worldZ) {
        return worldToSample(worldX, worldZ).map(Sample::pixel);
    }

    public Optional<Sample> worldToSample(double worldX, double worldZ) {
        if (!isWorldInsideMap(worldX, worldZ)) return Optional.empty();
        double u = (worldX - worldMinX) / (worldMaxX - worldMinX);
        double v = (worldZ - worldMinZ) / (worldMaxZ - worldMinZ);
        if (flipX) u = 1.0 - u;
        if (flipZ) v = 1.0 - v;
        int pixelX = clamp((int) Math.floor(u * imageWidth), 0, imageWidth - 1);
        int pixelY = clamp((int) Math.floor(v * imageHeight), 0, imageHeight - 1);
        return Optional.of(new Sample(u, v, new PixelPoint(pixelX, pixelY)));
    }

    public WorldPoint pixelToWorld(double pixelX, double pixelY) {
        double u = imageWidth == 1 ? 0.0 : pixelX / (imageWidth - 1.0);
        double v = imageHeight == 1 ? 0.0 : pixelY / (imageHeight - 1.0);
        if (flipX) u = 1.0 - u;
        if (flipZ) v = 1.0 - v;
        return new WorldPoint(worldMinX + u * (worldMaxX - worldMinX),
                worldMinZ + v * (worldMaxZ - worldMinZ));
    }

    public boolean isWorldInsideMap(double worldX, double worldZ) {
        return worldX >= worldMinX && worldX <= worldMaxX && worldZ >= worldMinZ && worldZ <= worldMaxZ;
    }

    public boolean isPixelInsideMap(int pixelX, int pixelY) {
        return pixelX >= 0 && pixelX < imageWidth && pixelY >= 0 && pixelY < imageHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public double worldMinX() { return worldMinX; }
    public double worldMaxX() { return worldMaxX; }
    public double worldMinZ() { return worldMinZ; }
    public double worldMaxZ() { return worldMaxZ; }
    public boolean flipX() { return flipX; }
    public boolean flipZ() { return flipZ; }
    public int imageWidth() { return imageWidth; }
    public int imageHeight() { return imageHeight; }

    public record PixelPoint(int x, int y) {}
    public record WorldPoint(double x, double z) {}
    public record Sample(double u, double v, PixelPoint pixel) {}
}
