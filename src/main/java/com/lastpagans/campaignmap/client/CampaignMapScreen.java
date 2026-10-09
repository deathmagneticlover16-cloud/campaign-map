package com.lastpagans.campaignmap.client;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.ProvinceDefinition;
import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.geography.MapProjection;
import com.lastpagans.campaignmap.geography.ProvinceRaster;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.Objects;

public final class CampaignMapScreen extends Screen {
    private static final ResourceLocation FRONTEND = new ResourceLocation(CampaignMapMod.MOD_ID,
            "campaign/campaign_front.png");
    private static final int PANEL_WIDTH = 220;
    private final ProvinceRegistry registry = ProvinceRegistry.get();
    private DynamicTexture overlayTexture;
    private ResourceLocation overlayLocation;
    private double scale = 1.0;
    private double panX;
    private double panY;
    private boolean initialLayout = true;
    private boolean dragging;
    private boolean politicalMode = true;
    private long renderedRevision = -1;
    @Nullable private ResourceLocation selectedProvince;
    @Nullable private ResourceLocation hoveredProvince;
    @Nullable private ResourceLocation renderedSelection;
    @Nullable private ResourceLocation renderedHover;

    public CampaignMapScreen() {
        super(Component.translatable("screen.campaignmap.title"));
    }

    @Override
    protected void init() {
        if (initialLayout) {
            int availableWidth = Math.max(64, width - PANEL_WIDTH - 32);
            int availableHeight = Math.max(64, height - 32);
            scale = Math.min(availableWidth / (double) registry.raster().width(),
                    availableHeight / (double) registry.raster().height());
            scale = Math.max(0.25, Math.min(4.0, scale));
            initialLayout = false;
        }
        NativeImage pixels = new NativeImage(registry.raster().width(), registry.raster().height(), true);
        overlayTexture = new DynamicTexture(pixels);
        overlayLocation = minecraft.getTextureManager().register("campaignmap/overlay", overlayTexture);
        rebuildOverlay();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101317);
        updateHover(mouseX, mouseY);
        if (needsOverlayRebuild()) rebuildOverlay();

        int viewportRight = Math.max(16, width - PANEL_WIDTH - 8);
        graphics.enableScissor(8, 8, viewportRight, height - 8);
        int drawX = mapDrawX();
        int drawY = mapDrawY();
        int drawWidth = Math.max(1, (int) Math.round(registry.raster().width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(registry.raster().height() * scale));
        graphics.blit(FRONTEND, drawX, drawY, 0, 0, drawWidth, drawHeight,
                registry.raster().width(), registry.raster().height());
        if (politicalMode && overlayLocation != null) {
            graphics.blit(overlayLocation, drawX, drawY, 0, 0, drawWidth, drawHeight,
                    registry.raster().width(), registry.raster().height());
        }
        renderLabels(graphics, drawX, drawY);
        graphics.disableScissor();
        renderPanel(graphics);
        graphics.drawString(font, Component.literal("Wheel: zoom  •  Right-drag: pan  •  P: political layer  •  Esc: close"),
                12, height - 17, 0xFFBBC2CB, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLabels(GuiGraphics graphics, int drawX, int drawY) {
        if (scale < 0.45) return;
        for (ProvinceDefinition province : registry.provinces()) {
            MapProjection.PixelPoint point = registry.labelPoint(province.id());
            if (point == null) continue;
            int x = (int) Math.round(drawX + (point.x() + 0.5) * scale);
            int y = (int) Math.round(drawY + (point.y() + 0.5) * scale);
            int textWidth = font.width(province.name());
            graphics.drawString(font, province.name(), x - textWidth / 2 + 1, y + 1, 0xCC000000, false);
            graphics.drawString(font, province.name(), x - textWidth / 2, y, 0xFFF3E9D0, false);
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        int left = width - PANEL_WIDTH;
        graphics.fill(left, 0, width, height, 0xE51A1E24);
        graphics.fill(left, 0, left + 2, height, 0xFF8D744B);
        graphics.drawString(font, title, left + 14, 15, 0xFFFFDDA0, false);
        graphics.drawString(font, politicalMode ? "POLITICAL MAP" : "TERRAIN MAP", left + 14, 31, 0xFFAEB8C4, false);
        ResourceLocation shownId = selectedProvince != null ? selectedProvince : hoveredProvince;
        int y = 60;
        if (shownId == null) {
            graphics.drawWordWrap(font, Component.literal("Move over a province, or left-click one to keep its details here."),
                    left + 14, y, PANEL_WIDTH - 28, 0xFFD6D8DC);
            return;
        }
        ProvinceDefinition province = registry.byId(shownId);
        if (province == null) return;
        ProvinceOwnerState state = ClientOwnershipCache.state(shownId);
        graphics.drawString(font, province.name().toUpperCase(), left + 14, y, 0xFFFFFFFF, false);
        y += 22;
        y = panelLine(graphics, left, y, "Province", province.id().toString());
        y = panelLine(graphics, left, y, "Owner", identityName(state.owner()));
        y = panelLine(graphics, left, y, "Controller", identityName(state.controller()));
        panelLine(graphics, left, y, "Neighbours", Integer.toString(registry.neighbours(shownId).size()));
        if (selectedProvince != null) {
            graphics.drawString(font, "SELECTED", left + 14, height - 38, 0xFF61DDF2, false);
        }
    }

    private int panelLine(GuiGraphics graphics, int left, int y, String label, String value) {
        graphics.drawString(font, label, left + 14, y, 0xFF9EA7B3, false);
        graphics.drawWordWrap(font, Component.literal(value), left + 14, y + 11, PANEL_WIDTH - 28, 0xFFE7E9EC);
        return y + 36;
    }

    private String identityName(@Nullable ProvinceOwnerState.PoliticalIdentity identity) {
        return identity == null ? "Unassigned" : identity.id() + "  " + identity.colorHex();
    }

    private void updateHover(double mouseX, double mouseY) {
        ProvinceDefinition province = provinceAtScreen(mouseX, mouseY);
        ResourceLocation id = province == null ? null : province.id();
        if (!Objects.equals(id, hoveredProvince)) hoveredProvince = id;
    }

    @Nullable
    private ProvinceDefinition provinceAtScreen(double mouseX, double mouseY) {
        if (mouseX < 8 || mouseX >= width - PANEL_WIDTH - 8 || mouseY < 8 || mouseY >= height - 8) return null;
        int pixelX = (int) Math.floor((mouseX - mapDrawX()) / scale);
        int pixelY = (int) Math.floor((mouseY - mapDrawY()) / scale);
        return registry.atPixel(pixelX, pixelY);
    }

    private boolean needsOverlayRebuild() {
        return renderedRevision != ClientOwnershipCache.revision()
                || !Objects.equals(renderedSelection, selectedProvince)
                || !Objects.equals(renderedHover, hoveredProvince);
    }

    private void rebuildOverlay() {
        NativeImage image = overlayTexture == null ? null : overlayTexture.getPixels();
        if (image == null) return;
        ProvinceRaster raster = registry.raster();
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                ProvinceDefinition province = registry.atPixel(x, y);
                int color = 0;
                if (province != null) {
                    ProvinceOwnerState state = ClientOwnershipCache.state(province.id());
                    if (state.owner() != null) color = abgr(state.owner().color(), 82);
                    if (province.id().equals(hoveredProvince)) color = abgr(0xFFFFFF, 42);
                    if (province.id().equals(selectedProvince)) color = abgr(0x49DDF5, 76);
                    Border border = borderAt(x, y, province);
                    if (border == Border.PROVINCE) color = abgr(0x151515, 185);
                    if (border == Border.POLITICAL) color = abgr(0xF4D27A, 235);
                    if (border != Border.NONE && province.id().equals(selectedProvince)) color = abgr(0x55E6FF, 255);
                }
                image.setPixelRGBA(x, y, color);
            }
        }
        overlayTexture.upload();
        renderedRevision = ClientOwnershipCache.revision();
        renderedSelection = selectedProvince;
        renderedHover = hoveredProvince;
    }

    private Border borderAt(int x, int y, ProvinceDefinition province) {
        boolean provinceBorder = false;
        boolean politicalBorder = false;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] direction : directions) {
            ProvinceDefinition neighbour = registry.atPixel(x + direction[0], y + direction[1]);
            if (neighbour == null || neighbour.id().equals(province.id())) continue;
            provinceBorder = true;
            String owner = ownerId(province.id());
            String neighbourOwner = ownerId(neighbour.id());
            if (!Objects.equals(owner, neighbourOwner)) politicalBorder = true;
        }
        return politicalBorder ? Border.POLITICAL : provinceBorder ? Border.PROVINCE : Border.NONE;
    }

    @Nullable
    private String ownerId(ResourceLocation provinceId) {
        ProvinceOwnerState.PoliticalIdentity owner = ClientOwnershipCache.state(provinceId).owner();
        return owner == null ? null : owner.id();
    }

    private static int abgr(int rgb, int alpha) {
        int red = (rgb >> 16) & 255;
        int green = (rgb >> 8) & 255;
        int blue = rgb & 255;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private int mapDrawX() {
        int viewportWidth = Math.max(1, width - PANEL_WIDTH - 16);
        return (int) Math.round(8 + viewportWidth / 2.0 - registry.raster().width() * scale / 2.0 + panX);
    }

    private int mapDrawY() {
        return (int) Math.round(height / 2.0 - registry.raster().height() * scale / 2.0 + panY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ProvinceDefinition province = provinceAtScreen(mouseX, mouseY);
            selectedProvince = province == null ? null : province.id();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double oldScale = scale;
        double mapPixelX = (mouseX - mapDrawX()) / oldScale;
        double mapPixelY = (mouseY - mapDrawY()) / oldScale;
        scale = Math.max(0.25, Math.min(8.0, scale * (amount > 0 ? 1.2 : 1.0 / 1.2)));
        int viewportWidth = Math.max(1, width - PANEL_WIDTH - 16);
        panX = mouseX - (8 + viewportWidth / 2.0 - registry.raster().width() * scale / 2.0) - mapPixelX * scale;
        panY = mouseY - (height / 2.0 - registry.raster().height() * scale / 2.0) - mapPixelY * scale;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_P) {
            politicalMode = !politicalMode;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (overlayLocation != null) Minecraft.getInstance().getTextureManager().release(overlayLocation);
        overlayTexture = null;
    }

    private enum Border { NONE, PROVINCE, POLITICAL }
}
