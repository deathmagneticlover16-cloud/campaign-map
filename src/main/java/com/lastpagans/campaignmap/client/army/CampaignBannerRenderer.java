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
    public static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation(CampaignMapMod.MOD_ID,
            "textures/misc/default_army_banner.png");
    private static final int STRIPS = 8;
    private static final double FLAG_WIDTH = 17.0;
    private static final double BANNER_HEIGHT = 6.0;

    public void render(GuiGraphics graphics, CampaignCamera3D camera, @Nullable UUID selectedArmy,
                       @Nullable UUID localPlayer, double animationSeconds) {
        Map<ResourceLocation, List<ClientArmyCache.Entry>> batches = new LinkedHashMap<>();
        for (ClientArmyCache.Entry entry : ClientArmyCache.entries()) {
            ResourceLocation texture = resolveTexture(entry.snapshot().flagTexture());
            batches.computeIfAbsent(texture, ignored -> new ArrayList<>()).add(entry);
        }
        if (batches.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        for (Map.Entry<ResourceLocation, List<ClientArmyCache.Entry>> batch : batches.entrySet()) {
            RenderSystem.setShaderTexture(0, batch.getKey());
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (ClientArmyCache.Entry entry : batch.getValue()) {
                boolean selected = entry.snapshot().armyId().equals(selectedArmy);
                boolean enemy = localPlayer != null && !entry.snapshot().ownerId().equals(localPlayer);
                appendFlag(buffer, matrix, camera, entry, selected, enemy, animationSeconds);
            }
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
                                   ClientArmyCache.Entry entry, boolean selected, boolean enemy,
                                   double animationSeconds) {
        Vec3 center = mapPosition(entry.displayedPosition());
        Vec3 cameraRight = camera.right();
        Vec3 horizontal = new Vec3(cameraRight.x, 0.0, cameraRight.z);
        if (horizontal.lengthSqr() < 1.0e-6) horizontal = new Vec3(1.0, 0.0, 0.0);
        else horizontal = horizontal.normalize();
        Vec3 cameraForward = camera.forward();
        Vec3 waveDirection = new Vec3(-cameraForward.x, 0.0, -cameraForward.z);
        if (waveDirection.lengthSqr() < 1.0e-6) waveDirection = new Vec3(0.0, 0.0, -1.0);
        else waveDirection = waveDirection.normalize();
        double aspect = clampAspect(entry.snapshot().flagAspectRatio());
        double flagHeight = FLAG_WIDTH / aspect;
        int red = selected ? 100 : enemy ? 255 : 255;
        int green = selected ? 235 : enemy ? 125 : 255;
        int blue = selected ? 255 : enemy ? 125 : 255;

        for (int strip = 0; strip < STRIPS; strip++) {
            double t0 = strip / (double) STRIPS;
            double t1 = (strip + 1) / (double) STRIPS;
            double local0 = (t0 - 0.5) * FLAG_WIDTH;
            double local1 = (t1 - 0.5) * FLAG_WIDTH;
            double wave0 = Math.sin(animationSeconds * 4.2 + t0 * Math.PI * 3.0) * 0.9 * t0;
            double wave1 = Math.sin(animationSeconds * 4.2 + t1 * Math.PI * 3.0) * 0.9 * t1;
            Vec3 base0 = center.add(horizontal.scale(local0)).add(waveDirection.scale(wave0));
            Vec3 base1 = center.add(horizontal.scale(local1)).add(waveDirection.scale(wave1));
            var bottom0 = camera.project(base0.x, BANNER_HEIGHT, base0.z);
            var bottom1 = camera.project(base1.x, BANNER_HEIGHT, base1.z);
            var top1 = camera.project(base1.x, BANNER_HEIGHT + flagHeight, base1.z);
            var top0 = camera.project(base0.x, BANNER_HEIGHT + flagHeight, base0.z);
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

    private static ResourceLocation resolveTexture(ResourceLocation requested) {
        return Minecraft.getInstance().getResourceManager().getResource(requested).isPresent()
                ? requested : DEFAULT_TEXTURE;
    }

    private static double clampAspect(double aspect) { return Math.max(0.5, Math.min(4.0, aspect)); }
}
