package com.lastpagans.campaignmap.api.army;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.lastpagans.campaignmap.army.CampaignArmyData;

/**
 * Integration point for Recruits RTS or another tactical-group owner.
 * Campaign Map never serializes soldiers or replaces the tactical mod's group records.
 */
public interface CampaignGroupProvider {
    boolean recognizes(ServerLevel level, UUID groupId);

    boolean canManage(ServerLevel level, UUID playerId, UUID groupId);

    int soldierCount(ServerLevel level, UUID groupId);

    default String groupName(ServerLevel level, UUID groupId) { return groupId.toString(); }

    /** Places the real tactical group after campaign deployment completes. */
    default void deploy(ServerLevel level, UUID groupId, Vec3 anchor) {}
    default void pack(ServerLevel level, UUID groupId) {}

    default void onEntityLoaded(ServerLevel level, Entity entity, CampaignArmyData armies) {}

    default List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) { return List.of(); }
}
