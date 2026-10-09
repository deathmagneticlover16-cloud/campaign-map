package com.lastpagans.campaignmap.geography;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProvinceRegistryTest {
    @Test
    void resolvesKnownWorldCornersAndInteriorSamples() {
        ProvinceRegistry registry = ProvinceRegistry.get();
        assertEquals("campaignmap:province_1", registry.atWorld(-200, -200).id().toString());
        assertEquals("campaignmap:province_0", registry.atWorld(199, -200).id().toString());
        assertEquals("campaignmap:province_2", registry.atWorld(-200, 199).id().toString());
        assertEquals("campaignmap:province_3", registry.atWorld(199, 199).id().toString());
        assertEquals("campaignmap:province_0", registry.atWorld(-0.5, -0.5).id().toString());
        assertEquals("campaignmap:province_1", registry.atWorld(-100.25, -100.25).id().toString());
        assertEquals("campaignmap:province_3", registry.atWorld(99.25, 99.25).id().toString());
    }

    @Test
    void derivesAdjacencyAndValidInteriorLabels() {
        ProvinceRegistry registry = ProvinceRegistry.get();
        registry.provinces().forEach(province -> {
            MapProjection.PixelPoint label = registry.labelPoint(province.id());
            assertEquals(province.id(), registry.atPixel(label.x(), label.y()).id());
        });
    }
}
