package com.lastpagans.campaignmap.api.army;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Immutable army state suitable for API consumers and network synchronization. */
public record CampaignArmySnapshot(UUID armyId,
                                   UUID ownerId,
                                   String armyName,
                                   @Nullable UUID commanderId,
                                   List<UUID> groupIds,
                                   ResourceLocation flagTexture,
                                   double flagAspectRatio,
                                   Vec3 campaignPosition,
                                   List<Vec3> movementPath,
                                   ArmyState state,
                                   int foodSupplies,
                                   double fatigue,
                                   int totalSoldiers,
                                   int transitionTicksRemaining,
                                   int transitionTicksTotal) {
    public CampaignArmySnapshot {
        groupIds = List.copyOf(groupIds);
        movementPath = List.copyOf(movementPath);
    }
}
