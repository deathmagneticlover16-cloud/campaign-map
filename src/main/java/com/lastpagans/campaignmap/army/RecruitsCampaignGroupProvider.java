package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.api.army.CampaignGroupProvider;
import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.talhanation.recruits.world.RecruitsGroup;
import com.talhanation.recruits.world.RecruitsGroupsSaveData;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
}
