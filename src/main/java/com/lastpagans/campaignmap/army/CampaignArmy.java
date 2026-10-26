package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import com.lastpagans.campaignmap.api.army.CommanderVisualSnapshot;
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
import java.util.LinkedHashMap;
import java.util.Map;

final class CampaignArmy {
    static final ResourceLocation DEFAULT_FLAG = new ResourceLocation("recruits_rts_camera",
            "textures/misc/betsy_ross_group_marker.png");

    final UUID armyId;
    final UUID ownerId;
    String name;
    @Nullable UUID commanderId;
    @Nullable CommanderVisualSnapshot commanderVisual;
    boolean commanderAvailable;
    @Nullable CompoundTag packedCommander;
    final List<UUID> groupIds = new ArrayList<>();
    ResourceLocation flagTexture = DEFAULT_FLAG;
    double flagAspectRatio = 1.5;
    Vec3 position;
    final List<Vec3> path = new ArrayList<>();
    ArmyState state = ArmyState.STATIONARY;
    int foodSupplies;
    double fatigue;
    int transitionTicks;
    int transitionTicksTotal;
    final Map<UUID, ListTag> packedEntities = new LinkedHashMap<>();

    CampaignArmy(UUID armyId, UUID ownerId, String name, @Nullable UUID commanderId,
                 @Nullable CommanderVisualSnapshot commanderVisual, Vec3 position) {
        this.armyId = armyId;
        this.ownerId = ownerId;
        this.name = name;
        this.commanderId = commanderId;
        this.commanderVisual = commanderVisual;
        this.commanderAvailable = commanderId != null;
        this.position = position;
    }

    CampaignArmySnapshot snapshot(ServerLevel level) {
        int soldiers = groupIds.stream().mapToInt(group -> CampaignGroupProviders.soldierCount(level, group)).sum();
        return new CampaignArmySnapshot(armyId, ownerId, name, commanderId, commanderVisual, commanderAvailable,
                groupIds, flagTexture,
                flagAspectRatio, position, path, state, foodSupplies, fatigue, soldiers,
                transitionTicks, transitionTicksTotal);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("armyId", armyId);
        tag.putUUID("ownerId", ownerId);
        tag.putString("name", name);
        if (commanderId != null) tag.putUUID("commanderId", commanderId);
        tag.putBoolean("commanderAvailable", commanderAvailable);
        if (commanderVisual != null) {
            CompoundTag visual = new CompoundTag();
            visual.putString("entityType", commanderVisual.entityType().toString());
            visual.putString("displayName", commanderVisual.displayName());
            visual.put("appearance", commanderVisual.appearance());
            tag.put("commanderVisual", visual);
        }
        if (packedCommander != null) tag.put("packedCommander", packedCommander.copy());
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
        tag.putInt("transitionTicks", transitionTicks);
        tag.putInt("transitionTicksTotal", transitionTicksTotal);
        ListTag packed = new ListTag();
        packedEntities.forEach((groupId, entities) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("group", groupId);
            entry.put("entities", entities.copy());
            packed.add(entry);
        });
        tag.put("packedEntities", packed);
        return tag;
    }

    static CampaignArmy load(CompoundTag tag) {
        CommanderVisualSnapshot commanderVisual = null;
        if (tag.contains("commanderVisual", Tag.TAG_COMPOUND)) {
            CompoundTag visual = tag.getCompound("commanderVisual");
            ResourceLocation type = ResourceLocation.tryParse(visual.getString("entityType"));
            if (type != null) commanderVisual = new CommanderVisualSnapshot(type,
                    visual.getString("displayName"), visual.getCompound("appearance"));
        }
        CampaignArmy army = new CampaignArmy(tag.getUUID("armyId"), tag.getUUID("ownerId"),
                tag.getString("name"), tag.hasUUID("commanderId") ? tag.getUUID("commanderId") : null,
                commanderVisual, getVec(tag, "position"));
        army.commanderAvailable = tag.contains("commanderAvailable")
                ? tag.getBoolean("commanderAvailable") : army.commanderId != null;
        if (army.commanderVisual == null) army.commanderAvailable = false;
        if (tag.contains("packedCommander", Tag.TAG_COMPOUND))
            army.packedCommander = tag.getCompound("packedCommander").copy();
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
        army.transitionTicks = Math.max(0, tag.getInt("transitionTicks"));
        army.transitionTicksTotal = Math.max(army.transitionTicks, tag.getInt("transitionTicksTotal"));
        for (Tag raw : tag.getList("packedEntities", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("group")) army.packedEntities.put(entry.getUUID("group"),
                    (ListTag) entry.getList("entities", Tag.TAG_COMPOUND).copy());
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
