package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CommanderVisualSnapshot;
import com.lastpagans.campaignmap.client.CampaignCamera3D;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Render-only horse and real Recruits commander proxies; neither proxy is added to the client level. */
public final class MountedCommanderRenderer {
    private static final ResourceLocation MUSTANG = new ResourceLocation("icys-better-horses", "mustang_horse");
    /** Number of campaign-map units occupied by one entity-space block. */
    private static final double MAP_UNITS_PER_BLOCK = 8.0D;
    /** Below Icy's ridden-trot threshold, selecting its walk gait. */
    private static final float ICY_WALK_SPEED = 0.24F;
    private static final EquipmentSlot[] RECRUITS_INVENTORY_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND
    };
    private final Map<UUID, Proxies> proxies = new HashMap<>();

    public void render(GuiGraphics graphics, CampaignCamera3D camera, float partialTick) {
        Set<UUID> live = new HashSet<>();
        for (ClientArmyCache.Entry entry : ClientArmyCache.entries()) {
            var army = entry.snapshot();
            if (!army.commanderAvailable() || army.commanderVisual() == null) continue;
            live.add(army.armyId());
            Proxies pair = proxies.get(army.armyId());
            if (pair == null || !pair.visual.equals(army.commanderVisual())) {
                Proxies replacement = create(army.armyId(), army.commanderVisual());
                if (replacement == null) continue;
                Proxies previous = proxies.put(army.armyId(), replacement);
                if (previous != null) previous.dispose();
                pair = replacement;
            }
            animate(pair, entry);
            Vec3 map = mapPosition(entry.displayedPosition());
            var projected = camera.project(map.x, 0.0D, map.z);
            if (!projected.visible()) continue;
            renderPair(graphics, camera, pair, map, entry.bodyYaw(), partialTick);
        }
        proxies.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            entry.getValue().dispose();
            return true;
        });
    }

    private static Proxies create(UUID armyId, CommanderVisualSnapshot visual) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        try {
            EntityType<?> commanderType = ForgeRegistries.ENTITY_TYPES.getValue(visual.entityType());
            Entity rawCommander = commanderType == null ? null : commanderType.create(minecraft.level);
            if (!(rawCommander instanceof LivingEntity commander)) return null;
            CompoundTag appearance = visual.appearance();
            commander.load(appearance);
            restoreRecruitsEquipment(commander, appearance);
            commander.setCustomNameVisible(false);
            LivingEntity horse = createIcyHorse(armyId);
            if (horse == null) return null;
            int renderId = 1 + Math.floorMod(armyId.hashCode(), 1_000_000_000);
            horse.setId(-renderId);
            commander.setId(-renderId - 1);
            PlayerTeam renderTeam = attachRenderTeam(minecraft, armyId, commander);
            commander.startRiding(horse, true);
            return new Proxies(visual, horse, commander, renderTeam);
        } catch (LinkageError | RuntimeException error) {
            CampaignMapMod.LOGGER.warn("Could not create mounted campaign commander proxy", error);
            return null;
        }
    }

    /** Recruits stores its six equipped slots in its own Items inventory as well as Mob equipment. */
    private static void restoreRecruitsEquipment(LivingEntity commander, CompoundTag appearance) {
        for (Tag raw : appearance.getList("Items", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag itemTag)) continue;
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= RECRUITS_INVENTORY_SLOTS.length) continue;
            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty()) commander.setItemSlot(RECRUITS_INVENTORY_SLOTS[slot], stack);
        }
    }

    /** Makes Recruits' clothing layer use the synchronized Color value, as it does for a live grouped recruit. */
    private static PlayerTeam attachRenderTeam(Minecraft minecraft, UUID armyId, LivingEntity commander) {
        if (minecraft.level == null) return null;
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        String name = "cm" + armyId.toString().replace("-", "").substring(0, 14);
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) team = scoreboard.addPlayerTeam(name);
        scoreboard.addPlayerToTeam(commander.getScoreboardName(), team);
        return team;
    }

    private static LivingEntity createIcyHorse(UUID armyId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(MUSTANG);
        Entity entity = type == null ? null : type.create(minecraft.level);
        if (!(entity instanceof LivingEntity mustang)) {
            CampaignMapMod.LOGGER.error("Icy's Better Horses 2.1.6 did not register {}", MUSTANG);
            return null;
        }
        applyHorseAppearance(mustang, armyId);
        return mustang;
    }

    private static void applyHorseAppearance(LivingEntity horse, UUID armyId) {
        net.minecraft.nbt.CompoundTag appearance = new net.minecraft.nbt.CompoundTag();
        appearance.putInt("BH_Coat", Math.floorMod(armyId.hashCode(), 8));
        appearance.putBoolean("Tame", true);
        net.minecraft.nbt.CompoundTag saddle = new net.minecraft.nbt.CompoundTag();
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE).save(saddle);
        appearance.put("SaddleItem", saddle);
        horse.load(appearance);
        if (horse instanceof Horse vanillaHorse) vanillaHorse.setTamed(true);
    }

    private static void animate(Proxies pair, ClientArmyCache.Entry entry) {
        boolean advancing = entry.snapshot().state() == ArmyState.MOVING
                && !entry.snapshot().movementPath().isEmpty() && entry.movementSpeed() > 0.01D;
        float stride = advancing ? ICY_WALK_SPEED : 0.0F;
        Minecraft minecraft = Minecraft.getInstance();
        long targetTick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (pair.lastAnimationTick < 0) pair.lastAnimationTick = targetTick - 1;
        int simulatedTicks = (int) Math.min(5L, Math.max(0L, targetTick - pair.lastAnimationTick));
        for (int tick = 0; tick < simulatedTicks; tick++) {
            pair.horse.walkAnimation.update(stride, 0.45F);
        }
        pair.lastAnimationTick = targetTick;

        Vec3 current = entry.displayedPosition();
        Vec3 delta = pair.lastPosition == null ? Vec3.ZERO : current.subtract(pair.lastPosition);
        Vec3 motion = delta.horizontalDistanceSqr() < 1.0e-8D ? Vec3.ZERO
                : new Vec3(delta.x, 0.0D, delta.z).normalize().scale(advancing ? 0.18D : 0.0D);
        pair.lastPosition = current;
        pair.horse.setPos(current);
        pair.horse.setDeltaMovement(motion);
        pair.horse.setOnGround(true);
        pair.horse.setXRot(0.0F);
        pair.horse.xRotO = 0.0F;
        pair.horse.tickCount = (int) targetTick;
        pair.commander.tickCount = (int) targetTick;
        applySharedYaw(pair.horse, pair.commander, entry.bodyYaw());
        pair.horse.positionRider(pair.commander);
        pair.commander.setDeltaMovement(motion);
        pair.commander.setOnGround(true);
        pair.commander.setXRot(0.0F);
        pair.commander.xRotO = 0.0F;
    }

    /** The Mustang owns travel rotation; the rider mirrors it without interpolation competition. */
    private static void applySharedYaw(LivingEntity horse, LivingEntity rider, float yaw) {
        horse.setYRot(yaw);
        horse.yRotO = yaw;
        horse.setYBodyRot(yaw);
        horse.yBodyRotO = yaw;
        horse.setYHeadRot(yaw);
        horse.yHeadRotO = yaw;

        rider.setYRot(yaw);
        rider.yRotO = yaw;
        rider.setYBodyRot(yaw);
        rider.yBodyRotO = yaw;
        rider.setYHeadRot(yaw);
        rider.yHeadRotO = yaw;
    }

    private static void renderPair(GuiGraphics graphics, CampaignCamera3D camera, Proxies pair,
                                   Vec3 mapAnchor,
                                   float yaw, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.mulPoseMatrix(mapSpaceTransform(camera, mapAnchor));
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        dispatcher.setRenderShadow(false);
        var buffers = minecraft.renderBuffers().bufferSource();
        dispatcher.render(pair.horse, 0.0D, 0.0D, 0.0D, yaw, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        Vec3 riderOffset = pair.commander.position().subtract(pair.horse.position());
        dispatcher.render(pair.commander, riderOffset.x, riderOffset.y, riderOffset.z,
                yaw, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        pose.popPose();
    }

    /**
     * Maps entity-local X/up/Z into fixed campaign-map X/elevation/Z axes.  Building
     * the basis from projected map points keeps the model planted on the map while
     * the campaign camera orbits; no camera-facing billboard rotation is involved.
     */
    private static Matrix4f mapSpaceTransform(CampaignCamera3D camera, Vec3 anchor) {
        var origin = camera.project(anchor.x, 0.0D, anchor.z);
        var mapX = camera.project(anchor.x + MAP_UNITS_PER_BLOCK, 0.0D, anchor.z);
        var mapUp = camera.project(anchor.x, MAP_UNITS_PER_BLOCK, anchor.z);
        var mapZ = camera.project(anchor.x, 0.0D, anchor.z + MAP_UNITS_PER_BLOCK);

        float originDepth = renderDepth(origin);
        float xDepth = modelDepthDelta(origin, mapX);
        float upDepth = modelDepthDelta(origin, mapUp);
        float zDepth = modelDepthDelta(origin, mapZ);
        return new Matrix4f(
                (float) (mapX.x() - origin.x()), (float) (mapX.y() - origin.y()), xDepth, 0.0F,
                (float) (mapUp.x() - origin.x()), (float) (mapUp.y() - origin.y()), upDepth, 0.0F,
                (float) (mapZ.x() - origin.x()), (float) (mapZ.y() - origin.y()), zDepth, 0.0F,
                (float) origin.x(), (float) origin.y(), originDepth, 1.0F);
    }

    private static float renderDepth(CampaignCamera3D.ProjectedPoint point) {
        return (float) Math.max(-500.0D, Math.min(500.0D, 300.0D - point.depth() * 0.01D));
    }

    /** Preserve depth between skin, clothing, hats, and armor instead of flattening their surfaces together. */
    private static float modelDepthDelta(CampaignCamera3D.ProjectedPoint origin,
                                         CampaignCamera3D.ProjectedPoint point) {
        return (float) (origin.depth() - point.depth());
    }

    public void close() {
        proxies.values().forEach(Proxies::dispose);
        proxies.clear();
    }

    private static Vec3 mapPosition(Vec3 worldPosition) {
        var registry = ProvinceRegistry.get();
        var sample = registry.projection().worldToSample(worldPosition.x, worldPosition.z).orElse(null);
        if (sample == null) return new Vec3(-10_000.0D, 0.0D, -10_000.0D);
        return new Vec3(sample.u() * registry.pixelMap().width(), 0.0D,
                sample.v() * registry.pixelMap().height());
    }

    private static final class Proxies {
        private final CommanderVisualSnapshot visual;
        private final LivingEntity horse;
        private final LivingEntity commander;
        private final PlayerTeam renderTeam;
        private long lastAnimationTick = -1L;
        private Vec3 lastPosition;

        private Proxies(CommanderVisualSnapshot visual, LivingEntity horse, LivingEntity commander,
                        PlayerTeam renderTeam) {
            this.visual = visual;
            this.horse = horse;
            this.commander = commander;
            this.renderTeam = renderTeam;
        }

        private void dispose() {
            Minecraft minecraft = Minecraft.getInstance();
            if (renderTeam == null || minecraft.level == null) return;
            Scoreboard scoreboard = minecraft.level.getScoreboard();
            scoreboard.removePlayerFromTeam(commander.getScoreboardName(), renderTeam);
            if (renderTeam.getPlayers().isEmpty()) scoreboard.removePlayerTeam(renderTeam);
        }
    }
}
