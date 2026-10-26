package com.lastpagans.campaignmap.api.army;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Only the data required to reconstruct a render-only commander proxy on clients. */
public record CommanderVisualSnapshot(ResourceLocation entityType, String displayName, CompoundTag appearance) {
    public CommanderVisualSnapshot {
        appearance = appearance.copy();
    }

    @Override
    public CompoundTag appearance() {
        return appearance.copy();
    }
}
