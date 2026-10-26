package com.lastpagans.campaignmap.api.army;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.lastpagans.campaignmap.army.CampaignArmyData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

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
    default void deploy(ServerLevel level, UUID groupId, Vec3 anchor, ListTag packedEntities) {}
    default ListTag pack(ServerLevel level, UUID groupId) { return new ListTag(); }

    default void onEntityLoaded(ServerLevel level, Entity entity, CampaignArmyData armies) {}

    default List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) { return List.of(); }

    default boolean recognizesCommander(ServerLevel level, UUID commanderId) { return false; }
    default boolean canCommand(ServerLevel level, UUID playerId, UUID commanderId) { return false; }
    default List<CampaignCommanderSummary> ownedCommanders(ServerLevel level, UUID playerId) { return List.of(); }
    default Optional<CommanderVisualSnapshot> commanderVisual(ServerLevel level, UUID commanderId) {
        return Optional.empty();
    }
    default Optional<CompoundTag> packCommander(ServerLevel level, UUID commanderId) { return Optional.empty(); }
    default void deployCommander(ServerLevel level, UUID commanderId, Vec3 anchor, CompoundTag packedCommander) {}
}
