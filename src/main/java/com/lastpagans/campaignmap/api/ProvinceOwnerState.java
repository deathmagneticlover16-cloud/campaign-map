package com.lastpagans.campaignmap.api;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record ProvinceOwnerState(ResourceLocation provinceId,
                                 @Nullable PoliticalIdentity owner,
                                 @Nullable PoliticalIdentity controller) {
    public record PoliticalIdentity(String id, int color) {
        public String colorHex() {
            return String.format("#%06X", color & 0xFFFFFF);
        }
    }
}
