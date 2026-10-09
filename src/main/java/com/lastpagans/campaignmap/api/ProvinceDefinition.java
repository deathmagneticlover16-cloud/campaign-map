package com.lastpagans.campaignmap.api;

import net.minecraft.resources.ResourceLocation;

/** Immutable geographic identity. Ownership is deliberately stored elsewhere. */
public record ProvinceDefinition(ResourceLocation id, String name, int rgb) {
    public String rgbHex() {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
