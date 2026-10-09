package com.lastpagans.campaignmap.geography;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class ProvinceRaster {
    private final int width;
    private final int height;
    private final int[] pixels;

    private ProvinceRaster(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public static ProvinceRaster read(InputStream stream) throws IOException {
        BufferedImage image = ImageIO.read(stream);
        if (image == null) throw new IOException("Unsupported or corrupt province raster PNG");
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        for (int i = 0; i < pixels.length; i++) pixels[i] &= 0xFFFFFF;
        return new ProvinceRaster(image.getWidth(), image.getHeight(), pixels);
    }

    public int rgbAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) throw new IndexOutOfBoundsException(x + "," + y);
        return pixels[y * width + x];
    }

    public int width() { return width; }
    public int height() { return height; }
}
