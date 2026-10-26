package com.lastpagans.campaignmap.api.army;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.lastpagans.campaignmap.army.CampaignArmyData;
import net.minecraft.nbt.ListTag;

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

    public static String groupName(ServerLevel level, UUID groupId) {
        return PROVIDERS.stream().filter(provider -> provider.recognizes(level, groupId))
                .map(provider -> provider.groupName(level, groupId)).findFirst().orElse("Unknown group");
    }

    public static void deploy(ServerLevel level, UUID groupId, Vec3 anchor, ListTag packedEntities) {
        PROVIDERS.stream().filter(provider -> provider.recognizes(level, groupId)).findFirst()
                .ifPresent(provider -> provider.deploy(level, groupId, anchor, packedEntities));
    }
    public static ListTag pack(ServerLevel level, UUID groupId) {
        return PROVIDERS.stream().filter(provider -> provider.recognizes(level, groupId)).findFirst()
                .map(provider -> provider.pack(level, groupId)).orElseGet(ListTag::new);
    }

    public static void onEntityLoaded(ServerLevel level, Entity entity, CampaignArmyData armies) {
        PROVIDERS.forEach(provider -> provider.onEntityLoaded(level, entity, armies));
    }

    public static List<CampaignGroupSummary> ownedGroups(ServerLevel level, UUID playerId) {
        LinkedHashMap<UUID, CampaignGroupSummary> groups = new LinkedHashMap<>();
        PROVIDERS.forEach(provider -> provider.ownedGroups(level, playerId)
                .forEach(group -> groups.putIfAbsent(group.groupId(), group)));
        return List.copyOf(groups.values());
    }
}
