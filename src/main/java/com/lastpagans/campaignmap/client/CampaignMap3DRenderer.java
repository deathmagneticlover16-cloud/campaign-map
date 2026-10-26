package com.lastpagans.campaignmap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class CampaignMap3DRenderer {
    private static final int GRID_SIZE = 24;

    private CampaignMap3DRenderer() {}

    public static void renderLayer(GuiGraphics graphics, CampaignCamera3D camera, ResourceLocation texture,
                                   int mapWidth, int mapHeight, int alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int gridY = 0; gridY < GRID_SIZE; gridY++) {
            double z0 = mapHeight * gridY / (double) GRID_SIZE;
            double z1 = mapHeight * (gridY + 1) / (double) GRID_SIZE;
            float v0 = gridY / (float) GRID_SIZE;
            float v1 = (gridY + 1) / (float) GRID_SIZE;
            for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
                double x0 = mapWidth * gridX / (double) GRID_SIZE;
                double x1 = mapWidth * (gridX + 1) / (double) GRID_SIZE;
                float u0 = gridX / (float) GRID_SIZE;
                float u1 = (gridX + 1) / (float) GRID_SIZE;
                var p00 = camera.project(x0, 0.0, z0);
                var p10 = camera.project(x1, 0.0, z0);
                var p11 = camera.project(x1, 0.0, z1);
                var p01 = camera.project(x0, 0.0, z1);
                // Never submit a quad that crosses the camera plane. Its behind-camera
                // corners otherwise project to the origin and stretch across the GUI.
                if (p00.depth() <= 1.0 || p10.depth() <= 1.0 || p11.depth() <= 1.0 || p01.depth() <= 1.0) continue;
                vertex(buffer, matrix, p00, u0, v0, alpha);
                vertex(buffer, matrix, p10, u1, v0, alpha);
                vertex(buffer, matrix, p11, u1, v1, alpha);
                vertex(buffer, matrix, p01, u0, v1, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, CampaignCamera3D.ProjectedPoint point,
                               float u, float v, int alpha) {
        float z = (float) Math.max(-500.0, Math.min(500.0, 200.0 - point.depth() * 0.01));
        buffer.vertex(matrix, (float) point.x(), (float) point.y(), z)
                .uv(u, v).color(255, 255, 255, alpha).endVertex();
    }
}
