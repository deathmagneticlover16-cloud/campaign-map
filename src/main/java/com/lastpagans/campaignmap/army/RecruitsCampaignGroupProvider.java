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
import com.talhanation.recruits.entities.CommanderEntity;
import com.lastpagans.campaignmap.api.army.CampaignCommanderSummary;
import com.lastpagans.campaignmap.api.army.CommanderVisualSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraftforge.registries.ForgeRegistries;

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
        resetTacticalFormation(level, groupId, anchor, group.getPlayerUUID());
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
        if (entity instanceof CommanderEntity commander && armies.hasCommander(commander.getUUID())) {
            armies.updateCommanderVisual(commander.getUUID(), visual(commander));
            var state = armies.stateForCommander(commander.getUUID()).orElse(null);
            if (state != null && state != com.lastpagans.campaignmap.api.army.ArmyState.DEPLOYED) {
                CompoundTag packed = new CompoundTag();
                if (commander.save(packed)) {
                    armies.setPackedCommander(commander.getUUID(), packed);
                    commander.discard();
                }
                return;
            }
        }
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

    @Override
    public boolean recognizesCommander(ServerLevel level, UUID commanderId) {
        return findCommander(level, commanderId) != null;
    }

    @Override
    public boolean canCommand(ServerLevel level, UUID playerId, UUID commanderId) {
        CommanderEntity commander = findCommander(level, commanderId);
        return commander != null && commander.isAlive() && playerId.equals(commander.getOwnerUUID());
    }

    @Override
    public List<CampaignCommanderSummary> ownedCommanders(ServerLevel level, UUID playerId) {
        List<CampaignCommanderSummary> result = new ArrayList<>();
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            for (Entity entity : candidate.getAllEntities()) {
                if (entity instanceof CommanderEntity commander && commander.isAlive()
                        && playerId.equals(commander.getOwnerUUID())) {
                    result.add(new CampaignCommanderSummary(commander.getUUID(), commanderName(commander)));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<CommanderVisualSnapshot> commanderVisual(ServerLevel level, UUID commanderId) {
        CommanderEntity commander = findCommander(level, commanderId);
        return commander == null || !commander.isAlive() ? Optional.empty() : Optional.of(visual(commander));
    }

    @Override
    public Optional<CompoundTag> packCommander(ServerLevel level, UUID commanderId) {
        CommanderEntity commander = findCommander(level, commanderId);
        if (commander == null || !commander.isAlive()) return Optional.empty();
        CompoundTag packed = new CompoundTag();
        if (!commander.save(packed)) return Optional.empty();
        commander.discard();
        return Optional.of(packed);
    }

    @Override
    public void deployCommander(ServerLevel level, UUID commanderId, Vec3 anchor, CompoundTag packedCommander) {
        if (packedCommander == null || packedCommander.isEmpty() || findCommander(level, commanderId) != null) return;
        Entity restored = EntityType.loadEntityRecursive(packedCommander, level, entity -> {
            if (!(entity instanceof CommanderEntity) || !commanderId.equals(entity.getUUID())) return null;
            place(level, entity, anchor, 0);
            return entity;
        });
        if (restored != null) level.addFreshEntityWithPassengers(restored);
    }

    private static CommanderEntity findCommander(ServerLevel level, UUID commanderId) {
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(commanderId);
            if (entity instanceof CommanderEntity commander) return commander;
        }
        return null;
    }

    private static CommanderVisualSnapshot visual(CommanderEntity commander) {
        CompoundTag full = new CompoundTag();
        commander.save(full);
        CompoundTag appearance = new CompoundTag();
        for (String key : List.of("Variant", "Color", "Biome", "CustomName", "CustomNameVisible",
                "ArmorItems", "HandItems", "Items", "BeforeItemSlot", "LeftHanded")) {
            Tag value = full.get(key);
            if (value != null) appearance.put(key, value.copy());
        }
        var typeId = ForgeRegistries.ENTITY_TYPES.getKey(commander.getType());
        if (typeId == null) typeId = new net.minecraft.resources.ResourceLocation("recruits", "patrol_leader");
        return new CommanderVisualSnapshot(typeId, commanderName(commander), appearance);
    }

    private static String commanderName(CommanderEntity commander) {
        String name = commander.getDisplayName().getString();
        if (name.isBlank()) name = "Commander";
        return name.substring(0, Math.min(128, name.length()));
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
        if (entity instanceof AbstractRecruitEntity recruit) {
            recruit.getNavigation().stop();
            recruit.setTarget(null);
            recruit.clearMovePos();
            recruit.setShouldMovePos(false);
            recruit.setHoldPos(recruit.position());
            recruit.setFollowState(2);
            recruit.setDeltaMovement(0.0D, recruit.getDeltaMovement().y, 0.0D);
        }
    }

    /** Retarget the optional RTS runtime so it cannot steer recruits back to the pre-campaign anchor. */
    private static void resetTacticalFormation(ServerLevel level, UUID groupId, Vec3 anchor, UUID ownerId) {
        if (!ModList.get().isLoaded("recruits_rts_camera")) return;
        try {
            Class<?> runtime = Class.forName("ee.lastpagans.recruitsrtscamera.formation.FormationRuntime");
            Method setCenter = runtime.getMethod("setFormationCenter", UUID.class, Vec3.class);
            Method halt = runtime.getMethod("haltFormation", UUID.class);
            setCenter.invoke(null, groupId, anchor);
            halt.invoke(null, groupId);

            Class<?> movement = Class.forName("ee.lastpagans.recruitsrtscamera.movement.RTSMovementBoostManager");
            movement.getMethod("cancelForcedOrders", java.util.Collection.class).invoke(null, List.of(groupId));

            Class<?> attacks = Class.forName("ee.lastpagans.recruitsrtscamera.combat.RTSGroupAttackManager");
            attacks.getMethod("cancel", java.util.Collection.class).invoke(null, List.of(groupId));

            net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                Class<?> routes = Class.forName("ee.lastpagans.recruitsrtscamera.network.RouteManager");
                routes.getMethod("cancel", net.minecraft.server.level.ServerPlayer.class, List.class)
                        .invoke(null, owner, List.of(groupId));
            }
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            // RTS is optional and older builds may not expose the stable formation API.
        } catch (ReflectiveOperationException | RuntimeException error) {
            com.lastpagans.campaignmap.CampaignMapMod.LOGGER.warn(
                    "Could not reset the RTS formation for deployed group {}", groupId, error);
        }
    }
}
