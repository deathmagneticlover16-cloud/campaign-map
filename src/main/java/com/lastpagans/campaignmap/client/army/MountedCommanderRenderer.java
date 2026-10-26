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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Render-only horse and real Recruits commander proxies; neither proxy is added to the client level. */
public final class MountedCommanderRenderer {
    private static final double MODEL_MAP_SCALE = 8.0D;
    private final Map<UUID, Proxies> proxies = new HashMap<>();

    public void render(GuiGraphics graphics, CampaignCamera3D camera, double animationSeconds, float partialTick) {
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
            animate(pair, entry, animationSeconds);
            Vec3 map = mapPosition(entry.displayedPosition());
            var projected = camera.project(map.x, 0.45D, map.z);
            if (!projected.visible()) continue;
            float scale = (float) Math.max(5.0D, Math.min(24.0D,
                    camera.pixelsPerMapUnit(projected.depth()) * MODEL_MAP_SCALE));
            renderPair(graphics, pair, projected.x(), projected.y(), scale, entry.bodyYaw(), partialTick);
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
        Set<String> icyNamespaces = new HashSet<>();
        ModList.get().getMods().stream()
                .filter(info -> {
                    String name = info.getDisplayName().toLowerCase(java.util.Locale.ROOT);
                    return name.contains("icy") && name.contains("horse");
                })
                .forEach(info -> icyNamespaces.add(info.getModId()));
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || !icyNamespaces.contains(id.getNamespace())
                    || !id.getPath().contains("horse")) continue;
            Entity entity = type.create(minecraft.level);
            if (entity instanceof LivingEntity living) {
                applyHorseAppearance(living, armyId);
                return living;
            }
        }
        Horse horse = EntityType.HORSE.create(minecraft.level);
        if (horse != null) {
            horse.setTamed(true);
            applyHorseAppearance(horse, armyId);
        }
        return horse;
    }

    private static void applyHorseAppearance(LivingEntity horse, UUID armyId) {
        net.minecraft.nbt.CompoundTag appearance = new net.minecraft.nbt.CompoundTag();
        appearance.putInt("Variant", Math.floorMod(armyId.hashCode(), 35));
        appearance.putBoolean("Tame", true);
        net.minecraft.nbt.CompoundTag saddle = new net.minecraft.nbt.CompoundTag();
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE).save(saddle);
        appearance.put("SaddleItem", saddle);
        horse.load(appearance);
    }

    private static void animate(Proxies pair, ClientArmyCache.Entry entry, double seconds) {
        float speed = switch (entry.snapshot().state()) {
            case MOVING -> entry.snapshot().movementPath().isEmpty() ? 0.0F : 0.65F;
            case RETREATING -> 0.0F;
            default -> 0.0F;
        };
        if (entry.movementSpeed() > 0.05D) speed = Math.max(speed, 0.35F);
        int ticks = (int) (seconds * 20.0D);
        for (LivingEntity entity : new LivingEntity[]{pair.horse, pair.commander}) {
            entity.tickCount = ticks;
            entity.setYRot(entry.bodyYaw());
            entity.setYBodyRot(entry.bodyYaw());
            entity.setYHeadRot(entry.bodyYaw());
            entity.walkAnimation.update(speed, 0.35F);
        }
    }

    private static void renderPair(GuiGraphics graphics, Proxies pair, double x, double y, float scale,
                                   float yaw, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        PoseStack pose = graphics.pose();
        Quaternionf oldOrientation = new Quaternionf(dispatcher.cameraOrientation());
        pose.pushPose();
        pose.translate(x, y + scale * 1.35F, 250.0D);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        dispatcher.overrideCameraOrientation(Axis.XP.rotationDegrees(12.0F));
        dispatcher.setRenderShadow(false);
        var buffers = minecraft.renderBuffers().bufferSource();
        dispatcher.render(pair.horse, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        pose.pushPose();
        pose.translate(0.0D, pair.horse.getPassengersRidingOffset(), 0.0D);
        dispatcher.render(pair.commander, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
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

    private record Proxies(CommanderVisualSnapshot visual, LivingEntity horse, LivingEntity commander) {}
}
