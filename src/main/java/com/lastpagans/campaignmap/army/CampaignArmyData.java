package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

public final class CampaignArmyData extends SavedData {
    private static final String DATA_NAME = "campaignmap_armies";
    private static final double DEFAULT_MOVEMENT_SPEED = 8.0;
    private final Map<UUID, CampaignArmy> armies = new LinkedHashMap<>();
    private final Map<UUID, UUID> groupMembership = new HashMap<>();

    public static CampaignArmyData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(CampaignArmyData::load, CampaignArmyData::new, DATA_NAME);
    }

    public static CampaignArmyData load(CompoundTag root) {
        CampaignArmyData data = new CampaignArmyData();
        for (Tag raw : root.getList("armies", Tag.TAG_COMPOUND)) {
            CampaignArmy army = CampaignArmy.load((CompoundTag) raw);
            data.armies.put(army.armyId, army);
            for (Iterator<UUID> iterator = army.groupIds.iterator(); iterator.hasNext();) {
                UUID group = iterator.next();
                UUID existing = data.groupMembership.putIfAbsent(group, army.armyId);
                if (existing != null) iterator.remove();
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        armies.values().forEach(army -> list.add(army.save()));
        root.put("armies", list);
        return root;
    }

    public CampaignArmySnapshot create(ServerLevel level, UUID ownerId, String name, @Nullable UUID commanderId, Vec3 position) {
        CampaignArmy army = new CampaignArmy(UUID.randomUUID(), ownerId, sanitizeName(name), commanderId, position);
        armies.put(army.armyId, army);
        setDirty();
        return army.snapshot(level);
    }

    public Optional<CampaignArmySnapshot> snapshot(ServerLevel level, UUID armyId) {
        CampaignArmy army = armies.get(armyId);
        return army == null ? Optional.empty() : Optional.of(army.snapshot(level));
    }

    public List<CampaignArmySnapshot> snapshots(ServerLevel level) {
        return armies.values().stream().map(army -> army.snapshot(level)).toList();
    }

    public boolean owns(UUID armyId, UUID playerId) {
        CampaignArmy army = armies.get(armyId);
        return army != null && army.ownerId.equals(playerId);
    }

    public boolean isGroupAssigned(UUID groupId) { return groupMembership.containsKey(groupId); }

    public boolean rename(ServerLevel level, UUID armyId, String name) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return false;
        army.name = sanitizeName(name);
        setDirty();
        return true;
    }

    public MembershipResult addGroup(ServerLevel level, UUID armyId, UUID playerId, UUID groupId, boolean bypassOwnership) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return MembershipResult.ARMY_NOT_FOUND;
        if (groupMembership.containsKey(groupId)) return MembershipResult.ALREADY_ASSIGNED;
        if (!bypassOwnership && (!CampaignGroupProviders.hasProviderFor(level, groupId)
                || !CampaignGroupProviders.canManage(level, playerId, groupId))) return MembershipResult.NOT_ALLOWED;
        army.groupIds.add(groupId);
        groupMembership.put(groupId, armyId);
        setDirty();
        return MembershipResult.SUCCESS;
    }

    public boolean removeGroup(UUID armyId, UUID groupId) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || !army.groupIds.remove(groupId)) return false;
        groupMembership.remove(groupId, armyId);
        setDirty();
        return true;
    }

    public boolean setMovementOrder(UUID armyId, Vec3 destination) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || army.state == ArmyState.DEPLOYED || army.state == ArmyState.DEPLOYING) return false;
        army.path.clear();
        army.path.add(destination);
        army.state = ArmyState.MOVING;
        setDirty();
        return true;
    }

    public boolean delete(UUID armyId) {
        CampaignArmy removed = armies.remove(armyId);
        if (removed == null) return false;
        removed.groupIds.forEach(group -> groupMembership.remove(group, armyId));
        setDirty();
        return true;
    }

    /** Advances server-authoritative positions and returns armies whose clients need refreshing. */
    public List<CampaignArmySnapshot> tick(ServerLevel level) {
        List<CampaignArmySnapshot> changed = new ArrayList<>();
        for (CampaignArmy army : armies.values()) {
            if ((army.state != ArmyState.MOVING && army.state != ArmyState.RETREATING) || army.path.isEmpty()) continue;
            Vec3 destination = army.path.get(0);
            Vec3 delta = destination.subtract(army.position);
            double remaining = delta.horizontalDistance();
            double fatigueMultiplier = Math.max(0.35, 1.0 - army.fatigue * 0.005);
            double step = DEFAULT_MOVEMENT_SPEED * fatigueMultiplier / 20.0;
            if (remaining <= step || remaining < 1.0e-5) {
                army.position = destination;
                army.path.remove(0);
                if (army.path.isEmpty()) army.state = ArmyState.STATIONARY;
            } else {
                Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z).normalize();
                army.position = army.position.add(horizontal.scale(step));
            }
            army.fatigue = Math.min(100.0, army.fatigue + 0.0025);
            changed.add(army.snapshot(level));
        }
        if (!changed.isEmpty()) setDirty();
        return changed;
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        return trimmed.isEmpty() ? "Unnamed Army" : trimmed.substring(0, Math.min(64, trimmed.length()));
    }

    public enum MembershipResult { SUCCESS, ARMY_NOT_FOUND, ALREADY_ASSIGNED, NOT_ALLOWED }
}
