package com.lastpagans.campaignmap.geography;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MapProjectionTest {
    private final MapProjection projection = new MapProjection(-200, 199, -200, 199,
            false, false, 384, 384);

    @Test
    void mapsAllInclusiveWorldCornersWithoutOverflow() {
        assertEquals(new MapProjection.PixelPoint(0, 0), projection.worldToPixel(-200, -200).orElseThrow());
        assertEquals(new MapProjection.PixelPoint(383, 0), projection.worldToPixel(199, -200).orElseThrow());
        assertEquals(new MapProjection.PixelPoint(0, 383), projection.worldToPixel(-200, 199).orElseThrow());
        assertEquals(new MapProjection.PixelPoint(383, 383), projection.worldToPixel(199, 199).orElseThrow());
    }

    @Test
    void mapsCenterAndInteriorByNormalizedCoordinates() {
        MapProjection.Sample center = projection.worldToSample(-0.5, -0.5).orElseThrow();
        assertEquals(0.5, center.u(), 1.0e-9);
        assertEquals(0.5, center.v(), 1.0e-9);
        assertEquals(new MapProjection.PixelPoint(192, 192), center.pixel());
        assertEquals(new MapProjection.PixelPoint(96, 96), projection.worldToPixel(-100.25, -100.25).orElseThrow());
        assertEquals(new MapProjection.PixelPoint(288, 288), projection.worldToPixel(99.25, 99.25).orElseThrow());
    }

    @Test
    void rejectsCoordinatesOutsideConfiguredBounds() {
        assertTrue(projection.worldToPixel(-200.01, 0).isEmpty());
        assertTrue(projection.worldToPixel(199.01, 0).isEmpty());
        assertTrue(projection.worldToPixel(0, -200.01).isEmpty());
        assertTrue(projection.worldToPixel(0, 199.01).isEmpty());
    }

    @Test
    void flipsOnlyAxesExplicitlyConfigured() {
        MapProjection flipped = new MapProjection(-200, 199, -200, 199,
                true, true, 384, 384);
        assertEquals(new MapProjection.PixelPoint(383, 383), flipped.worldToPixel(-200, -200).orElseThrow());
        assertEquals(new MapProjection.PixelPoint(0, 0), flipped.worldToPixel(199, 199).orElseThrow());
    }
}
