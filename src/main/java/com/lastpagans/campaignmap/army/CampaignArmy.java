package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CampaignArmy {
    static final ResourceLocation DEFAULT_FLAG = new ResourceLocation(CampaignMapMod.MOD_ID,
            "textures/misc/default_army_banner.png");

    final UUID armyId;
    final UUID ownerId;
    String name;
    @Nullable UUID commanderId;
    final List<UUID> groupIds = new ArrayList<>();
    ResourceLocation flagTexture = DEFAULT_FLAG;
    double flagAspectRatio = 1.5;
    Vec3 position;
    final List<Vec3> path = new ArrayList<>();
    ArmyState state = ArmyState.STATIONARY;
    int foodSupplies;
    double fatigue;

    CampaignArmy(UUID armyId, UUID ownerId, String name, @Nullable UUID commanderId, Vec3 position) {
        this.armyId = armyId;
        this.ownerId = ownerId;
        this.name = name;
        this.commanderId = commanderId;
        this.position = position;
    }

    CampaignArmySnapshot snapshot(ServerLevel level) {
        int soldiers = groupIds.stream().mapToInt(group -> CampaignGroupProviders.soldierCount(level, group)).sum();
        return new CampaignArmySnapshot(armyId, ownerId, name, commanderId, groupIds, flagTexture,
                flagAspectRatio, position, path, state, foodSupplies, fatigue, soldiers);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("armyId", armyId);
        tag.putUUID("ownerId", ownerId);
        tag.putString("name", name);
        if (commanderId != null) tag.putUUID("commanderId", commanderId);
        ListTag groups = new ListTag();
        groupIds.forEach(group -> groups.add(StringTag.valueOf(group.toString())));
        tag.put("groups", groups);
        tag.putString("flagTexture", flagTexture.toString());
        tag.putDouble("flagAspectRatio", flagAspectRatio);
        putVec(tag, "position", position);
        ListTag pathTag = new ListTag();
        path.forEach(point -> {
            CompoundTag pointTag = new CompoundTag();
            putVec(pointTag, "point", point);
            pathTag.add(pointTag);
        });
        tag.put("path", pathTag);
        tag.putString("state", state.name());
        tag.putInt("foodSupplies", foodSupplies);
        tag.putDouble("fatigue", fatigue);
        return tag;
    }

    static CampaignArmy load(CompoundTag tag) {
        CampaignArmy army = new CampaignArmy(tag.getUUID("armyId"), tag.getUUID("ownerId"),
                tag.getString("name"), tag.hasUUID("commanderId") ? tag.getUUID("commanderId") : null,
                getVec(tag, "position"));
        for (Tag groupTag : tag.getList("groups", Tag.TAG_STRING)) {
            try { army.groupIds.add(UUID.fromString(groupTag.getAsString())); }
            catch (IllegalArgumentException ignored) {}
        }
        ResourceLocation flag = ResourceLocation.tryParse(tag.getString("flagTexture"));
        if (flag != null) army.flagTexture = flag;
        army.flagAspectRatio = clampAspect(tag.contains("flagAspectRatio") ? tag.getDouble("flagAspectRatio") : 1.5);
        for (Tag pathEntry : tag.getList("path", Tag.TAG_COMPOUND)) {
            army.path.add(getVec((CompoundTag) pathEntry, "point"));
        }
        try { army.state = ArmyState.valueOf(tag.getString("state")); }
        catch (IllegalArgumentException ignored) { army.state = ArmyState.STATIONARY; }
        army.foodSupplies = Math.max(0, tag.getInt("foodSupplies"));
        army.fatigue = Math.max(0.0, tag.getDouble("fatigue"));
        if (army.path.isEmpty() && (army.state == ArmyState.MOVING || army.state == ArmyState.RETREATING)) {
            army.state = ArmyState.STATIONARY;
        }
        return army;
    }

    static double clampAspect(double aspect) {
        return Math.max(0.5, Math.min(4.0, aspect));
    }

    private static void putVec(CompoundTag parent, String key, Vec3 value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", value.x);
        tag.putDouble("y", value.y);
        tag.putDouble("z", value.z);
        parent.put(key, tag);
    }

    private static Vec3 getVec(CompoundTag parent, String key) {
        CompoundTag tag = parent.getCompound(key);
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}
