package com.lastpagans.campaignmap.api.army;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.List;

/**
 * Integration point for Recruits RTS or another tactical-group owner.
 * Campaign Map never serializes soldiers or replaces the tactical mod's group records.
 */
public interface CampaignGroupProvider {
    boolean recognizes(ServerLevel level, UUID groupId);

    boolean canManage(ServerLevel level, UUID playerId, UUID groupId);

    int soldierCount(ServerLevel level, UUID groupId);

    default List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) { return List.of(); }
}
