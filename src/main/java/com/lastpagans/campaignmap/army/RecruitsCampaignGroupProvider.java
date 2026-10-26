package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.api.army.CampaignGroupProvider;
import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.world.RecruitsGroupsSaveData;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;

/** Optional adapter over Recruits' authoritative persistent group records. */
public final class RecruitsCampaignGroupProvider implements CampaignGroupProvider {
    @Override
    public boolean recognizes(ServerLevel level, UUID groupId) {
        return find(level, groupId) != null;
    }

    @Override
    public boolean canManage(ServerLevel level, UUID playerId, UUID groupId) {
        RecruitsGroup group = find(level, groupId);
        return group != null && playerId.equals(group.getPlayerUUID());
    }

    @Override
    public int soldierCount(ServerLevel level, UUID groupId) {
        RecruitsGroup group = find(level, groupId);
        return group == null || group.members == null ? 0 : group.members.size();
    }

    @Override
    public String groupName(ServerLevel level, UUID groupId) {
        RecruitsGroup group = find(level, groupId);
        return group == null ? "Unknown group" : group.getName();
    }

    @Override
    public void deploy(ServerLevel level, UUID groupId, Vec3 anchor, ListTag packedEntities) {
        RecruitsGroup group = find(level, groupId);
        if (group == null || group.members == null) return;
        int[] restoredIndex = {0};
        for (Tag raw : packedEntities) {
            if (!(raw instanceof CompoundTag entityTag)) continue;
            Entity restored = EntityType.loadEntityRecursive(entityTag, level, entity -> {
                place(level, entity, anchor, restoredIndex[0]++);
                return entity;
            });
            if (restored != null) level.addFreshEntityWithPassengers(restored);
        }
        int index = 0;
        for (UUID member : group.members) {
            Entity entity = null;
            for (ServerLevel candidate : level.getServer().getAllLevels()) {
                entity = candidate.getEntity(member);
                if (entity != null) break;
            }
            if (entity == null || !entity.isAlive()) continue;
            if (entity.level() != level) {
                Entity moved = entity.changeDimension(level);
                if (moved == null) continue;
                entity = moved;
            }
            place(level, entity, anchor, index);
            index++;
        }
    }

    @Override
    public ListTag pack(ServerLevel level, UUID groupId) {
        ListTag packed = new ListTag();
        RecruitsGroup group = find(level, groupId);
        if (group == null || group.members == null) return packed;
        for (UUID member : group.members) for (ServerLevel candidate : level.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(member);
            if (entity instanceof AbstractRecruitEntity recruit) {
                CompoundTag tag = new CompoundTag();
                if (recruit.save(tag)) {
                    packed.add(tag);
                    recruit.discard();
                }
                break;
            }
        }
        return packed;
    }

    @Override
    public void onEntityLoaded(ServerLevel level, Entity entity, CampaignArmyData armies) {
        if (!(entity instanceof AbstractRecruitEntity recruit) || recruit.getGroup() == null) return;
        var state = armies.stateForGroup(recruit.getGroup()).orElse(null);
        if (state != com.lastpagans.campaignmap.api.army.ArmyState.DEPLOYED) {
            if (state != null) {
                CompoundTag tag = new CompoundTag();
                if (recruit.save(tag)) {
                    armies.addPackedEntity(recruit.getGroup(), tag);
                    recruit.discard();
                }
            }
            return;
        }
        Vec3 anchor = armies.deployedAnchor(recruit.getGroup()).orElse(null);
        if (anchor == null) return;
        RecruitsGroup group = find(level, recruit.getGroup());
        int index = group == null || group.members == null ? 0 : Math.max(0, group.members.indexOf(recruit.getUUID()));
        place(level, recruit, anchor, index);
    }

    @Override
    public List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) {
        List<CampaignGroupSummary> result = new ArrayList<>();
        for (RecruitsGroup group : RecruitsGroupsSaveData.get(level).getAllGroups()) {
            if (group == null || group.removed || !playerId.equals(group.getPlayerUUID())) continue;
            int soldiers = group.members == null ? 0 : group.members.size();
            result.add(new CampaignGroupSummary(group.getUUID(), group.getName(), soldiers));
        }
        return List.copyOf(result);
    }

    private static RecruitsGroup find(ServerLevel level, UUID groupId) {
        for (RecruitsGroup group : RecruitsGroupsSaveData.get(level).getAllGroups()) {
            if (group != null && !group.removed && groupId.equals(group.getUUID())) return group;
        }
        return null;
    }

    private static void place(ServerLevel level, Entity entity, Vec3 anchor, int index) {
        if (entity instanceof AbstractRecruitEntity recruit) {
            recruit.setNoAi(false);
            recruit.setInvisible(false);
            recruit.setInvulnerable(false);
        }
        double x = anchor.x + (index % 8) * 2.0, z = anchor.z + (index / 8) * 2.0;
        double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
        entity.teleportTo(x, y, z);
    }
}
