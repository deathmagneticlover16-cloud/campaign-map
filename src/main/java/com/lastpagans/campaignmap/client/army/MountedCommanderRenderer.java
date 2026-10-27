package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CommanderVisualSnapshot;
import com.lastpagans.campaignmap.client.CampaignCamera3D;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Render-only horse and real Recruits commander proxies; neither proxy is added to the client level. */
public final class MountedCommanderRenderer {
    private static final ResourceLocation MUSTANG = new ResourceLocation("icys-better-horses", "mustang_horse");
    private static final double MODEL_MAP_SCALE = 8.0D;
    private final Map<UUID, Proxies> proxies = new HashMap<>();

    public void render(GuiGraphics graphics, CampaignCamera3D camera, float partialTick) {
        Set<UUID> live = new HashSet<>();
        for (ClientArmyCache.Entry entry : ClientArmyCache.entries()) {
            var army = entry.snapshot();
            if (!army.commanderAvailable() || army.commanderVisual() == null
                    || army.state() == ArmyState.DEPLOYED) continue;
            live.add(army.armyId());
            Proxies pair = proxies.get(army.armyId());
            if (pair == null || !pair.visual.equals(army.commanderVisual())) {
                pair = create(army.armyId(), army.commanderVisual());
                if (pair == null) continue;
                proxies.put(army.armyId(), pair);
            }
            animate(pair, entry);
            Vec3 map = mapPosition(entry.displayedPosition());
            var projected = camera.project(map.x, 0.05D, map.z);
            if (!projected.visible()) continue;
            float scale = (float) Math.max(5.0D, Math.min(24.0D,
                    camera.pixelsPerMapUnit(projected.depth()) * MODEL_MAP_SCALE));
            renderPair(graphics, camera, pair, projected.x(), projected.y(), scale, entry.bodyYaw(), partialTick);
        }
        proxies.keySet().removeIf(id -> !live.contains(id));
    }

    private static Proxies create(UUID armyId, CommanderVisualSnapshot visual) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        try {
            EntityType<?> commanderType = ForgeRegistries.ENTITY_TYPES.getValue(visual.entityType());
            Entity rawCommander = commanderType == null ? null : commanderType.create(minecraft.level);
            if (!(rawCommander instanceof LivingEntity commander)) return null;
            commander.load(visual.appearance());
            LivingEntity horse = createIcyHorse(armyId);
            if (horse == null) return null;
            int renderId = 1 + Math.floorMod(armyId.hashCode(), 1_000_000_000);
            horse.setId(-renderId);
            commander.setId(-renderId - 1);
            commander.startRiding(horse, true);
            return new Proxies(visual, horse, commander);
        } catch (LinkageError | RuntimeException error) {
            CampaignMapMod.LOGGER.warn("Could not create mounted campaign commander proxy", error);
            return null;
        }
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
        float stride = advancing ? 0.82F : 0.0F;
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
        pair.horse.tickCount = (int) targetTick;
        pair.commander.tickCount = (int) targetTick;
        for (LivingEntity entity : new LivingEntity[]{pair.horse, pair.commander}) {
            entity.setYRot(entry.bodyYaw());
            entity.setYBodyRot(entry.bodyYaw());
            entity.setYHeadRot(entry.bodyYaw());
        }
        pair.horse.positionRider(pair.commander);
        pair.commander.setDeltaMovement(motion);
        pair.commander.setOnGround(true);
    }

    private static void renderPair(GuiGraphics graphics, CampaignCamera3D camera, Proxies pair,
                                   double x, double y, float scale,
                                   float yaw, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        PoseStack pose = graphics.pose();
        Quaternionf oldOrientation = new Quaternionf(dispatcher.cameraOrientation());
        pose.pushPose();
        pose.translate(x, y, 250.0D);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - (yaw - camera.yawDegrees())));
        dispatcher.overrideCameraOrientation(Axis.YP.rotationDegrees(-camera.yawDegrees()));
        dispatcher.setRenderShadow(false);
        var buffers = minecraft.renderBuffers().bufferSource();
        dispatcher.render(pair.horse, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        Vec3 riderOffset = pair.commander.position().subtract(pair.horse.position());
        dispatcher.render(pair.commander, riderOffset.x, riderOffset.y, riderOffset.z,
                0.0F, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        dispatcher.overrideCameraOrientation(oldOrientation);
        pose.popPose();
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
        private long lastAnimationTick = -1L;
        private Vec3 lastPosition;

        private Proxies(CommanderVisualSnapshot visual, LivingEntity horse, LivingEntity commander) {
            this.visual = visual;
            this.horse = horse;
            this.commander = commander;
        }
    }
}
