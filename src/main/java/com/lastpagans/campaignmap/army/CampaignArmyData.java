package com.lastpagans.campaignmap.army;

import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import com.lastpagans.campaignmap.api.army.CommanderVisualSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class CampaignArmyData extends SavedData {
    private static final String DATA_NAME = "campaignmap_armies";
    private static final double DEFAULT_MOVEMENT_SPEED = 8.0;
    private final Map<UUID, CampaignArmy> armies = new LinkedHashMap<>();
    private final Map<UUID, UUID> groupMembership = new HashMap<>();
    private final Map<UUID, UUID> commanderMembership = new HashMap<>();
    private int commanderRefreshTicker;

    public static CampaignArmyData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(CampaignArmyData::load, CampaignArmyData::new, DATA_NAME);
    }

    public static CampaignArmyData load(CompoundTag root) {
        CampaignArmyData data = new CampaignArmyData();
        for (Tag raw : root.getList("armies", Tag.TAG_COMPOUND)) {
            CampaignArmy army = CampaignArmy.load((CompoundTag) raw);
            data.armies.put(army.armyId, army);
            if (army.commanderId != null) {
                UUID existingCommander = data.commanderMembership.putIfAbsent(army.commanderId, army.armyId);
                if (existingCommander != null) {
                    army.commanderId = null;
                    army.commanderVisual = null;
                    army.commanderAvailable = false;
                }
            }
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

    public CampaignArmySnapshot create(ServerLevel level, UUID ownerId, String name, UUID commanderId, Vec3 position) {
        if (commanderMembership.containsKey(commanderId)) throw new IllegalArgumentException("Commander is already assigned");
        CommanderVisualSnapshot visual = CampaignGroupProviders.commanderVisual(level, commanderId)
                .orElseThrow(() -> new IllegalArgumentException("Commander is unavailable"));
        CampaignArmy army = new CampaignArmy(UUID.randomUUID(), ownerId, sanitizeName(name), commanderId, visual, position);
        army.state = ArmyState.DEPLOYED;
        armies.put(army.armyId, army);
        commanderMembership.put(commanderId, army.armyId);
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
    public boolean isCommanderAssigned(UUID commanderId) { return commanderMembership.containsKey(commanderId); }
    public boolean hasCommander(UUID commanderId) { return commanderMembership.containsKey(commanderId); }

    public boolean rename(ServerLevel level, UUID armyId, String name) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return false;
        army.name = sanitizeName(name);
        setDirty();
        return true;
    }

    public boolean setFlag(UUID armyId, net.minecraft.resources.ResourceLocation paintingId, double aspect) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return false;
        army.flagTexture = paintingId;
        army.flagAspectRatio = CampaignArmy.clampAspect(aspect);
        setDirty();
        return true;
    }

    public MembershipResult addGroup(ServerLevel level, UUID armyId, UUID playerId, UUID groupId, boolean bypassOwnership) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return MembershipResult.ARMY_NOT_FOUND;
        if (army.state != ArmyState.DEPLOYED && army.state != ArmyState.STATIONARY) return MembershipResult.NOT_ALLOWED;
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
        if (army == null || (army.state != ArmyState.DEPLOYED && army.state != ArmyState.STATIONARY)
                || !army.groupIds.remove(groupId)) return false;
        groupMembership.remove(groupId, armyId);
        setDirty();
        return true;
    }

    public boolean setMovementOrder(UUID armyId, Vec3 destination) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || army.state != ArmyState.MOVING) return false;
        army.path.clear();
        army.path.add(destination);
        setDirty();
        return true;
    }

    public boolean beginMovementMode(UUID armyId) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || !army.commanderAvailable
                || (army.state != ArmyState.DEPLOYED && army.state != ArmyState.STATIONARY)) return false;
        army.path.clear();
        army.state = ArmyState.RETREATING;
        army.transitionTicks = army.transitionTicksTotal = deploymentTicks(army.groupIds.size());
        setDirty();
        return true;
    }

    public boolean delete(UUID armyId) {
        CampaignArmy existing = armies.get(armyId);
        if (existing == null || (existing.state != ArmyState.DEPLOYED && existing.state != ArmyState.STATIONARY)) return false;
        CampaignArmy removed = armies.remove(armyId);
        if (removed == null) return false;
        removed.groupIds.forEach(group -> groupMembership.remove(group, armyId));
        if (removed.commanderId != null) commanderMembership.remove(removed.commanderId, armyId);
        setDirty();
        return true;
    }

    /** Advances server-authoritative positions and returns armies whose clients need refreshing. */
    public List<CampaignArmySnapshot> tick(ServerLevel level) {
        List<CampaignArmySnapshot> changed = new ArrayList<>();
        for (CampaignArmy army : armies.values()) {
            if (army.state == ArmyState.RETREATING) {
                if (--army.transitionTicks <= 0) {
                    army.transitionTicks = 0;
                    Optional<CompoundTag> commander = army.commanderId == null ? Optional.empty()
                            : CampaignGroupProviders.packCommander(level, army.commanderId);
                    if (commander.isEmpty()) {
                        army.state = ArmyState.DEPLOYED;
                        changed.add(army.snapshot(level));
                        continue;
                    }
                    army.packedCommander = commander.get().copy();
                    army.groupIds.forEach(group -> {
                        ListTag packed = CampaignGroupProviders.pack(level, group);
                        mergePacked(army, group, packed);
                    });
                    army.state = ArmyState.MOVING;
                }
                changed.add(army.snapshot(level));
                continue;
            }
            if (army.state == ArmyState.DEPLOYING) {
                if (--army.transitionTicks <= 0) {
                    army.transitionTicks = 0;
                    army.state = ArmyState.DEPLOYED;
                    deployGroups(level, army);
                }
                changed.add(army.snapshot(level));
                continue;
            }
            if (army.state != ArmyState.MOVING || army.path.isEmpty()) continue;
            Vec3 destination = army.path.get(0);
            Vec3 delta = destination.subtract(army.position);
            double remaining = delta.horizontalDistance();
            double fatigueMultiplier = Math.max(0.35, 1.0 - army.fatigue * 0.005);
            double step = DEFAULT_MOVEMENT_SPEED * fatigueMultiplier / 20.0;
            if (remaining <= step || remaining < 1.0e-5) {
                army.position = destination;
                army.path.remove(0);
            } else {
                Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z).normalize();
                army.position = army.position.add(horizontal.scale(step));
            }
            army.fatigue = Math.min(100.0, army.fatigue + 0.0025);
            changed.add(army.snapshot(level));
        }
        if (++commanderRefreshTicker >= 20) {
            commanderRefreshTicker = 0;
            for (CampaignArmy army : armies.values()) {
                if (army.commanderId == null || army.state != ArmyState.DEPLOYED) continue;
                CampaignGroupProviders.commanderVisual(level, army.commanderId).ifPresent(visual -> {
                    if (!visual.equals(army.commanderVisual) || !army.commanderAvailable) {
                        army.commanderVisual = visual;
                        army.commanderAvailable = true;
                        CampaignArmySnapshot snapshot = army.snapshot(level);
                        if (!changed.contains(snapshot)) changed.add(snapshot);
                    }
                });
            }
        }
        if (!changed.isEmpty()) setDirty();
        return changed;
    }

    public boolean beginDeployment(UUID armyId) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || army.state != ArmyState.MOVING) return false;
        army.path.clear();
        army.state = ArmyState.DEPLOYING;
        army.transitionTicks = army.transitionTicksTotal = deploymentTicks(army.groupIds.size());
        setDirty();
        return true;
    }

    private static int deploymentTicks(int groups) {
        int seconds = groups <= 2 ? 5 : groups <= 5 ? 10 : groups <= 10 ? 20 : 30;
        return seconds * 20;
    }

    private static void deployGroups(ServerLevel level, CampaignArmy army) {
        if (army.commanderId != null && army.packedCommander != null) {
            CampaignGroupProviders.deployCommander(level, army.commanderId, army.position, army.packedCommander);
            army.packedCommander = null;
        }
        int count = Math.max(1, army.groupIds.size());
        for (int i = 0; i < army.groupIds.size(); i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 anchor = army.position.add(Math.cos(angle) * 12.0, 0.0, Math.sin(angle) * 12.0);
            UUID groupId = army.groupIds.get(i);
            CampaignGroupProviders.deploy(level, groupId, anchor,
                    army.packedEntities.getOrDefault(groupId, new ListTag()));
            army.packedEntities.remove(groupId);
        }
    }

    public Optional<Vec3> deployedAnchor(UUID groupId) {
        UUID armyId = groupMembership.get(groupId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        if (army == null || army.state != ArmyState.DEPLOYED) return Optional.empty();
        int index = army.groupIds.indexOf(groupId);
        if (index < 0) return Optional.empty();
        double angle = Math.PI * 2.0 * index / Math.max(1, army.groupIds.size());
        return Optional.of(army.position.add(Math.cos(angle) * 12.0, 0.0, Math.sin(angle) * 12.0));
    }

    public boolean deployNow(ServerLevel level, UUID armyId) {
        CampaignArmy army = armies.get(armyId);
        if (army == null) return false;
        army.state = ArmyState.DEPLOYED;
        deployGroups(level, army);
        army.transitionTicks = 0;
        setDirty();
        return true;
    }

    public Optional<ArmyState> stateForGroup(UUID groupId) {
        UUID armyId = groupMembership.get(groupId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        return army == null ? Optional.empty() : Optional.of(army.state);
    }

    public Optional<ArmyState> stateForCommander(UUID commanderId) {
        UUID armyId = commanderMembership.get(commanderId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        return army == null ? Optional.empty() : Optional.of(army.state);
    }

    public void updateCommanderVisual(UUID commanderId, CommanderVisualSnapshot visual) {
        UUID armyId = commanderMembership.get(commanderId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        if (army == null) return;
        army.commanderVisual = visual;
        army.commanderAvailable = true;
        setDirty();
    }

    public void setPackedCommander(UUID commanderId, CompoundTag packed) {
        UUID armyId = commanderMembership.get(commanderId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        if (army == null) return;
        army.packedCommander = packed.copy();
        setDirty();
    }

    public Optional<CampaignArmySnapshot> markCommanderUnavailable(ServerLevel level, UUID commanderId) {
        UUID armyId = commanderMembership.get(commanderId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        if (army == null) return Optional.empty();
        army.commanderAvailable = false;
        army.packedCommander = null;
        setDirty();
        return Optional.of(army.snapshot(level));
    }

    public Optional<CampaignArmySnapshot> appointCommander(ServerLevel level, UUID armyId, UUID ownerId, UUID commanderId) {
        CampaignArmy army = armies.get(armyId);
        if (army == null || !army.ownerId.equals(ownerId)
                || (army.state != ArmyState.DEPLOYED && army.state != ArmyState.STATIONARY)
                || commanderMembership.containsKey(commanderId)
                || !CampaignGroupProviders.canCommand(level, ownerId, commanderId)) return Optional.empty();
        CommanderVisualSnapshot visual = CampaignGroupProviders.commanderVisual(level, commanderId).orElse(null);
        if (visual == null) return Optional.empty();
        if (army.commanderId != null) commanderMembership.remove(army.commanderId, armyId);
        army.commanderId = commanderId;
        army.commanderVisual = visual;
        army.commanderAvailable = true;
        army.packedCommander = null;
        commanderMembership.put(commanderId, armyId);
        setDirty();
        return Optional.of(army.snapshot(level));
    }

    public void addPackedEntity(UUID groupId, CompoundTag entityTag) {
        UUID armyId = groupMembership.get(groupId);
        CampaignArmy army = armyId == null ? null : armies.get(armyId);
        if (army == null || army.state == ArmyState.DEPLOYED) return;
        ListTag list = army.packedEntities.computeIfAbsent(groupId, ignored -> new ListTag());
        UUID entityId = entityTag.hasUUID("UUID") ? entityTag.getUUID("UUID") : null;
        if (entityId != null) for (Tag raw : list) {
            CompoundTag existing = (CompoundTag) raw;
            if (existing.hasUUID("UUID") && entityId.equals(existing.getUUID("UUID"))) return;
        }
        list.add(entityTag.copy());
        setDirty();
    }

    private void mergePacked(CampaignArmy army, UUID groupId, ListTag incoming) {
        if (incoming.isEmpty()) return;
        ListTag target = army.packedEntities.computeIfAbsent(groupId, ignored -> new ListTag());
        for (Tag raw : incoming) {
            CompoundTag added = (CompoundTag) raw;
            UUID id = added.hasUUID("UUID") ? added.getUUID("UUID") : null;
            boolean duplicate = false;
            if (id != null) for (Tag existingRaw : target) {
                CompoundTag existing = (CompoundTag) existingRaw;
                if (existing.hasUUID("UUID") && id.equals(existing.getUUID("UUID"))) { duplicate = true; break; }
            }
            if (!duplicate) target.add(added.copy());
        }
        setDirty();
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        return trimmed.isEmpty() ? "Unnamed Army" : trimmed.substring(0, Math.min(64, trimmed.length()));
    }

    public enum MembershipResult { SUCCESS, ARMY_NOT_FOUND, ALREADY_ASSIGNED, NOT_ALLOWED }
}
