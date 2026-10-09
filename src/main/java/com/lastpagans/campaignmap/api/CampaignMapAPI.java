package com.lastpagans.campaignmap.api;

import com.lastpagans.campaignmap.data.CampaignOwnershipData;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/** Dedicated-server-safe entry point for other mods. */
public final class CampaignMapAPI {
    private CampaignMapAPI() {}

    public static Optional<ProvinceDefinition> getProvinceAt(Level level, BlockPos position) {
        return getProvinceAt(level, position.getX(), position.getZ());
    }

    public static Optional<ProvinceDefinition> getProvinceAt(Level level, double x, double z) {
        return Optional.ofNullable(ProvinceRegistry.get().atWorld(x, z));
    }

    public static Optional<ProvinceDefinition> getProvince(ResourceLocation id) {
        return Optional.ofNullable(ProvinceRegistry.get().byId(id));
    }

    public static Set<ResourceLocation> getNeighbours(ResourceLocation id) {
        return ProvinceRegistry.get().neighbours(id);
    }

    public static Optional<ProvinceOwnerState.PoliticalIdentity> getOwner(ServerLevel level, ResourceLocation provinceId) {
        return Optional.ofNullable(CampaignOwnershipData.get(level).state(provinceId).owner());
    }

    public static Optional<ProvinceOwnerState.PoliticalIdentity> getController(ServerLevel level, ResourceLocation provinceId) {
        return Optional.ofNullable(CampaignOwnershipData.get(level).state(provinceId).controller());
    }

    public static ProvinceOwnerState getPoliticalState(ServerLevel level, ResourceLocation provinceId) {
        return CampaignOwnershipData.get(level).state(provinceId);
    }
}
