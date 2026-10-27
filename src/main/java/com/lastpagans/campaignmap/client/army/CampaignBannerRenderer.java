package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.client.CampaignCamera3D;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.*;

/** Eight-strip animated cloth banners for the 3D campaign scene. */
public final class CampaignBannerRenderer {
    public static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("recruits_rts_camera",
            "textures/misc/betsy_ross_group_marker.png");
    private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation(CampaignMapMod.MOD_ID,
            "textures/misc/default_army_banner.png");
    private static final int STRIPS = 8;
    private static final double FLAG_WIDTH = 25.0;
    private static final double BANNER_HEIGHT = 13.0;

    public void render(GuiGraphics graphics, CampaignCamera3D camera, @Nullable UUID selectedArmy,
                       @Nullable UUID localPlayer, double animationSeconds) {
        Map<ResourceLocation, List<RenderFlag>> batches = new LinkedHashMap<>();
        for (ClientArmyCache.Entry entry : ClientArmyCache.entries()) {
            Vec3 center = mapPosition(entry.displayedPosition());
            var armyFlag = FlagTextureResolver.resolvePainting(entry.snapshot().flagTexture(), defaultTexture(), entry.snapshot().flagAspectRatio());
            boolean selected = entry.snapshot().armyId().equals(selectedArmy);
            boolean enemy = localPlayer != null && !entry.snapshot().ownerId().equals(localPlayer);
            batches.computeIfAbsent(armyFlag.texture(), ignored -> new ArrayList<>())
                    .add(new RenderFlag(center, armyFlag.aspect(), FLAG_WIDTH, BANNER_HEIGHT, selected, enemy));
            if (entry.snapshot().state() == com.lastpagans.campaignmap.api.army.ArmyState.DEPLOYED) {
                int count = Math.max(1, entry.snapshot().groupIds().size());
                for (int i = 0; i < entry.snapshot().groupIds().size(); i++) {
                    UUID groupId = entry.snapshot().groupIds().get(i);
                    var groupFlag = FlagTextureResolver.resolveGroup(groupId, defaultTexture());
                    double angle = Math.PI * 2.0 * i / count;
                    Vec3 groupCenter = center.add(Math.cos(angle) * 11.0, 0.0, Math.sin(angle) * 11.0);
                    batches.computeIfAbsent(groupFlag.texture(), ignored -> new ArrayList<>())
                            .add(new RenderFlag(groupCenter, groupFlag.aspect(), 7.0, 2.0, false, enemy));
                }
            }
        }
        if (batches.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        for (Map.Entry<ResourceLocation, List<RenderFlag>> batch : batches.entrySet()) {
            RenderSystem.setShaderTexture(0, batch.getKey());
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (RenderFlag flag : batch.getValue()) appendFlag(buffer, matrix, camera, flag, animationSeconds);
            BufferUploader.drawWithShader(buffer.end());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    @Nullable
    public UUID pick(CampaignCamera3D camera, double mouseX, double mouseY) {
        UUID best = null;
        double bestDistance = 22.0;
        for (ClientArmyCache.Entry entry : ClientArmyCache.entries()) {
            Vec3 map = mapPosition(entry.displayedPosition());
            double height = FLAG_WIDTH / clampAspect(entry.snapshot().flagAspectRatio());
            var projected = camera.project(map.x, BANNER_HEIGHT + height * 0.5, map.z);
            if (!projected.visible()) continue;
            double distance = Math.hypot(projected.x() - mouseX, projected.y() - mouseY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.snapshot().armyId();
            }
        }
        return best;
    }

    private static void appendFlag(BufferBuilder buffer, Matrix4f matrix, CampaignCamera3D camera,
                                   RenderFlag flag, double animationSeconds) {
        Vec3 center = flag.center;
        Vec3 cameraRight = camera.right();
        Vec3 horizontal = new Vec3(cameraRight.x, 0.0, cameraRight.z);
        if (horizontal.lengthSqr() < 1.0e-6) horizontal = new Vec3(1.0, 0.0, 0.0);
        else horizontal = horizontal.normalize();
        Vec3 cameraForward = camera.forward();
        Vec3 waveDirection = new Vec3(-cameraForward.x, 0.0, -cameraForward.z);
        if (waveDirection.lengthSqr() < 1.0e-6) waveDirection = new Vec3(0.0, 0.0, -1.0);
        else waveDirection = waveDirection.normalize();
        double aspect = clampAspect(flag.aspect);
        double flagHeight = flag.width / aspect;
        int red = flag.selected ? 100 : 255;
        int green = flag.selected ? 235 : flag.enemy ? 125 : 255;
        int blue = flag.selected ? 255 : flag.enemy ? 125 : 255;

        for (int strip = 0; strip < STRIPS; strip++) {
            double t0 = strip / (double) STRIPS;
            double t1 = (strip + 1) / (double) STRIPS;
            double local0 = (t0 - 0.5) * flag.width;
            double local1 = (t1 - 0.5) * flag.width;
            double wave0 = Math.sin(animationSeconds * 4.2 + t0 * Math.PI * 3.0) * 0.9 * t0;
            double wave1 = Math.sin(animationSeconds * 4.2 + t1 * Math.PI * 3.0) * 0.9 * t1;
            Vec3 base0 = center.add(horizontal.scale(local0)).add(waveDirection.scale(wave0));
            Vec3 base1 = center.add(horizontal.scale(local1)).add(waveDirection.scale(wave1));
            var bottom0 = camera.project(base0.x, flag.height, base0.z);
            var bottom1 = camera.project(base1.x, flag.height, base1.z);
            var top1 = camera.project(base1.x, flag.height + flagHeight, base1.z);
            var top0 = camera.project(base0.x, flag.height + flagHeight, base0.z);
            vertex(buffer, matrix, bottom0, (float) t0, 1.0F, red, green, blue);
            vertex(buffer, matrix, bottom1, (float) t1, 1.0F, red, green, blue);
            vertex(buffer, matrix, top1, (float) t1, 0.0F, red, green, blue);
            vertex(buffer, matrix, top0, (float) t0, 0.0F, red, green, blue);
        }
    }

    private static Vec3 mapPosition(Vec3 worldPosition) {
        var sample = ProvinceRegistry.get().projection().worldToSample(worldPosition.x, worldPosition.z).orElse(null);
        if (sample == null) return new Vec3(-10_000.0, 0.0, -10_000.0);
        return new Vec3(sample.u() * ProvinceRegistry.get().pixelMap().width(), 0.0,
                sample.v() * ProvinceRegistry.get().pixelMap().height());
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, CampaignCamera3D.ProjectedPoint point,
                               float u, float v, int red, int green, int blue) {
        float z = (float) Math.max(-500.0, Math.min(500.0, 300.0 - point.depth() * 0.01));
        buffer.vertex(matrix, (float) point.x(), (float) point.y(), z)
                .uv(u, v).color(red, green, blue, 255).endVertex();
    }

    private static ResourceLocation defaultTexture() {
        return Minecraft.getInstance().getResourceManager().getResource(DEFAULT_TEXTURE).isPresent() ? DEFAULT_TEXTURE : FALLBACK_TEXTURE;
    }

    private static double clampAspect(double aspect) { return Math.max(0.5, Math.min(4.0, aspect)); }
    private record RenderFlag(Vec3 center, double aspect, double width, double height, boolean selected, boolean enemy) {}
}
