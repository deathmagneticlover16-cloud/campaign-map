package com.lastpagans.campaignmap.api.army;

import com.lastpagans.campaignmap.army.CampaignArmyData;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dedicated-server-safe army query API for Settlements, Recruits RTS, and other integrations. */
public final class CampaignArmyAPI {
    private CampaignArmyAPI() {}

    public static Optional<CampaignArmySnapshot> getArmy(ServerLevel level, UUID armyId) {
        return CampaignArmyData.get(level).snapshot(level, armyId);
    }

    public static List<CampaignArmySnapshot> getArmies(ServerLevel level) {
        return CampaignArmyData.get(level).snapshots(level);
    }

    public static Optional<CampaignArmySnapshot> getArmyForGroup(ServerLevel level, UUID groupId) {
        return getArmies(level).stream().filter(army -> army.groupIds().contains(groupId)).findFirst();
    }
}
