package com.lastpagans.campaignmap.api.army;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CampaignGroupProviders {
    private static final List<CampaignGroupProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private CampaignGroupProviders() {}

    public static void register(CampaignGroupProvider provider) {
        if (!PROVIDERS.contains(provider)) PROVIDERS.add(provider);
    }

    public static boolean hasProviderFor(ServerLevel level, UUID groupId) {
        return PROVIDERS.stream().anyMatch(provider -> provider.recognizes(level, groupId));
    }

    public static boolean canManage(ServerLevel level, UUID playerId, UUID groupId) {
        return PROVIDERS.stream()
                .filter(provider -> provider.recognizes(level, groupId))
                .anyMatch(provider -> provider.canManage(level, playerId, groupId));
    }

    public static int soldierCount(ServerLevel level, UUID groupId) {
        return PROVIDERS.stream()
                .filter(provider -> provider.recognizes(level, groupId))
                .mapToInt(provider -> Math.max(0, provider.soldierCount(level, groupId)))
                .findFirst()
                .orElse(0);
    }

    public static List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) {
        LinkedHashMap<UUID, CampaignGroupSummary> groups = new LinkedHashMap<>();
        PROVIDERS.forEach(provider -> provider.ownedGroups(level, playerId)
                .forEach(group -> groups.putIfAbsent(group.groupId(), group)));
        return List.copyOf(groups.values());
    }
}
