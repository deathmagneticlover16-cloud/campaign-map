package com.lastpagans.campaignmap.client;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.ProvinceDefinition;
import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.client.army.CampaignBannerRenderer;
import com.lastpagans.campaignmap.client.army.ClientArmyCache;
import com.lastpagans.campaignmap.client.army.ClientGroupCatalog;
import com.lastpagans.campaignmap.client.army.MountedCommanderRenderer;
import com.lastpagans.campaignmap.geography.MapProjection;
import com.lastpagans.campaignmap.geography.ProvincePixelMap;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import java.util.Objects;

public final class CampaignMapScreen extends Screen {
    private static final ResourceLocation FRONTEND = new ResourceLocation(CampaignMapMod.MOD_ID,
            "campaign/campaign_front.png");
    private static final int PANEL_WIDTH = 260;
    private final ProvinceRegistry registry = ProvinceRegistry.get();
    private DynamicTexture overlayTexture;
    private ResourceLocation overlayLocation;
    private final CampaignCamera3D camera = new CampaignCamera3D();
    private final CampaignBannerRenderer bannerRenderer = new CampaignBannerRenderer();
    private final MountedCommanderRenderer mountedCommanderRenderer = new MountedCommanderRenderer();
    private boolean cameraInitialized;
    private boolean dragging;
    private boolean orbiting;
    private double dragDistance;
    private boolean politicalMode = true;
    private boolean armyManagementMode;
    private int groupListOffset;
    private int commanderListOffset;
    private final java.util.Set<java.util.UUID> pendingGroups = new java.util.LinkedHashSet<>();
    @Nullable private java.util.UUID pendingCommander;
    @Nullable private java.util.UUID appointingArmy;
    private long previousFrameNanos;
    private double animationSeconds;
    private long renderedRevision = -1;
    @Nullable private ResourceLocation selectedProvince;
    @Nullable private ResourceLocation hoveredProvince;
    @Nullable private java.util.UUID selectedArmy;
    @Nullable private java.util.UUID hoveredArmy;
    @Nullable private ResourceLocation renderedSelection;
    @Nullable private ResourceLocation renderedHover;
    private EditBox armyNameInput;
    private Button renameButton;
    private Button flagButton;
    private Button armyActionButton;
    private Button createArmyButton;
    @Nullable private java.util.UUID editedArmy;

    public CampaignMapScreen() {
        super(Component.translatable("screen.campaignmap.title"));
    }

    @Override
    protected void init() {
        if (!cameraInitialized) {
            camera.initialize(registry.pixelMap().width(), registry.pixelMap().height());
            cameraInitialized = true;
        }
        // init() runs again when the window is resized. Release the previous texture so
        // there is always exactly one campaign overlay registered with Minecraft.
        if (overlayLocation != null) {
            minecraft.getTextureManager().release(overlayLocation);
            overlayLocation = null;
            overlayTexture = null;
        }
        NativeImage pixels = new NativeImage(registry.pixelMap().width(), registry.pixelMap().height(), true);
        overlayTexture = new DynamicTexture(pixels);
        overlayLocation = minecraft.getTextureManager().register("campaignmap/overlay", overlayTexture);
        clampTextureToSingleImage(minecraft.getTextureManager().getTexture(FRONTEND));
        clampTextureToSingleImage(overlayTexture);
        rebuildOverlay();
        CampaignNetwork.requestGroupCatalog();
        int left = width - PANEL_WIDTH;
        armyNameInput = new EditBox(font, left + 14, 58, PANEL_WIDTH - 28, 20, Component.literal("Army name"));
        armyNameInput.setMaxLength(64);
        addRenderableWidget(armyNameInput);
        renameButton = addRenderableWidget(Button.builder(Component.literal("RENAME"), button -> renameSelectedArmy())
                .bounds(left + 14, 82, 108, 20).build());
        flagButton = addRenderableWidget(Button.builder(Component.literal("CHOOSE FLAG"), button -> chooseSelectedFlag())
                .bounds(left + 128, 82, 118, 20).build());
        armyActionButton = addRenderableWidget(Button.builder(Component.literal("PREPARE TO MOVE"), button -> runArmyAction())
                .bounds(left + 14, height - 42, PANEL_WIDTH - 28, 24).build());
        createArmyButton = addRenderableWidget(Button.builder(Component.literal("CREATE ARMY"), button -> createSelectedArmy())
                .bounds(left + 14, height - 42, PANEL_WIDTH - 28, 24).build());
        hideArmyControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101317);
        long now = System.nanoTime();
        double deltaSeconds = previousFrameNanos == 0L ? 0.0 : (now - previousFrameNanos) / 1_000_000_000.0;
        previousFrameNanos = now;
        animationSeconds += Math.max(0.0, Math.min(0.1, deltaSeconds));
        int viewportRight = Math.max(16, width - PANEL_WIDTH - 8);
        camera.viewport(8, 8, viewportRight - 8, Math.max(1, height - 16));
        camera.update(deltaSeconds);
        ClientArmyCache.smooth(deltaSeconds);
        updateHover(mouseX, mouseY);
        if (needsOverlayRebuild()) rebuildOverlay();

        graphics.enableScissor(8, 8, viewportRight, height - 8);
        renderMapTextures(graphics);
        renderLabels(graphics);
        java.util.UUID localPlayer = minecraft.player == null ? null : minecraft.player.getUUID();
        mountedCommanderRenderer.render(graphics, camera, partialTick);
        bannerRenderer.render(graphics, camera, selectedArmy, localPlayer, animationSeconds);
        graphics.disableScissor();
        renderPanel(graphics);
        syncArmyControls();
        graphics.drawString(font, Component.literal("Wheel: zoom  •  Right-click: order  •  Right-drag: pan  •  Middle-drag: orbit  •  A: armies"),
                12, height - 17, 0xFFBBC2CB, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMapTextures(GuiGraphics graphics) {
        int mapWidth = registry.pixelMap().width();
        int mapHeight = registry.pixelMap().height();
        CampaignMap3DRenderer.renderLayer(graphics, camera, FRONTEND, mapWidth, mapHeight, 255);
        if (politicalMode && overlayLocation != null) {
            CampaignMap3DRenderer.renderLayer(graphics, camera, overlayLocation, mapWidth, mapHeight, 255);
        }
    }

    private static void clampTextureToSingleImage(AbstractTexture texture) {
        texture.setFilter(false, false);
        texture.bind();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private void renderLabels(GuiGraphics graphics) {
        for (ProvinceDefinition province : registry.provinces()) {
            MapProjection.PixelPoint point = registry.labelPoint(province.id());
            if (point == null) continue;
            var projected = camera.project(point.x() + 0.5, 0.35, point.y() + 0.5);
            if (!projected.visible()) continue;
            int x = (int) Math.round(projected.x());
            int y = (int) Math.round(projected.y());
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
        if (armyManagementMode) {
            if (appointingArmy != null) renderCommanderAppointmentPanel(graphics, left);
            else renderArmyCreationPanel(graphics, left);
            return;
        }
        java.util.UUID shownArmyId = selectedArmy != null ? selectedArmy : hoveredArmy;
        if (shownArmyId != null) {
            ClientArmyCache.get(shownArmyId).ifPresent(entry -> renderArmyPanel(graphics, left, entry.snapshot()));
            return;
        }
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

    private void renderArmyCreationPanel(GuiGraphics graphics, int left) {
        graphics.drawString(font, "CREATE CAMPAIGN ARMY", left + 14, 58, 0xFFFFDDA0, false);
        graphics.drawString(font, "1. Select a Recruits commander", left + 14, 74, 0xFFAEB8C4, false);
        java.util.Set<java.util.UUID> assignedCommanders = new java.util.HashSet<>();
        ClientArmyCache.entries().forEach(entry -> {
            if (entry.snapshot().commanderId() != null) assignedCommanders.add(entry.snapshot().commanderId());
        });
        int commanderY = 91;
        int commanderRows = Math.min(3, ClientGroupCatalog.commanders().size());
        for (int i = 0; i < commanderRows; i++) {
            int commanderIndex = commanderListOffset + i;
            if (commanderIndex >= ClientGroupCatalog.commanders().size()) break;
            var commander = ClientGroupCatalog.commanders().get(commanderIndex);
            boolean unavailable = assignedCommanders.contains(commander.commanderId());
            boolean selected = commander.commanderId().equals(pendingCommander);
            graphics.fill(left + 10, commanderY - 3, width - 10, commanderY + 15,
                    unavailable ? 0x55343434 : selected ? 0xAA31596A : 0x88303842);
            graphics.drawString(font, font.plainSubstrByWidth(commander.name(), PANEL_WIDTH - 30), left + 14,
                    commanderY, unavailable ? 0xFF777777 : selected ? 0xFF8DEAFF : 0xFFE7E9EC, false);
            commanderY += 20;
        }
        if (ClientGroupCatalog.commanders().isEmpty())
            graphics.drawString(font, "No available Recruits commanders", left + 14, commanderY, 0xFFFF8888, false);
        int groupTitleY = creationGroupStartY();
        graphics.drawString(font, "2. Select groups", left + 14, groupTitleY - 17, 0xFFAEB8C4, false);
        java.util.Set<java.util.UUID> assigned = new java.util.HashSet<>();
        ClientArmyCache.entries().forEach(entry -> assigned.addAll(entry.snapshot().groupIds()));
        int y = groupTitleY;
        int shown = 0;
        int maxRows = creationGroupRows();
        for (var group : ClientGroupCatalog.groups().stream().skip(groupListOffset).toList()) {
            if (shown >= maxRows) break;
            boolean unavailable = assigned.contains(group.groupId());
            boolean selected = pendingGroups.contains(group.groupId());
            int background = unavailable ? 0x55343434 : selected ? 0xAA31596A : 0x88303842;
            graphics.fill(left + 10, y - 3, width - 10, y + 16, background);
            int color = unavailable ? 0xFF777777 : selected ? 0xFF8DEAFF : 0xFFE7E9EC;
            String label = group.name() + "  [" + group.soldierCount() + "]";
            graphics.drawString(font, font.plainSubstrByWidth(label, PANEL_WIDTH - 30), left + 14, y, color, false);
            y += 22;
            shown++;
        }
        if (ClientGroupCatalog.groups().size() > maxRows)
            graphics.drawString(font, "Wheel to browse groups", left + 14, height - 62, 0xFF8F98A3, false);
        if (ClientGroupCatalog.groups().isEmpty()) {
            graphics.drawWordWrap(font, Component.literal("No owned Recruits groups are available. The Recruits mod must be installed on the server."),
                    left + 14, y, PANEL_WIDTH - 28, 0xFFD6D8DC);
        }
    }

    private boolean handleArmyPanelClick(double mouseX, double mouseY) {
        int left = width - PANEL_WIDTH;
        if (!armyManagementMode || mouseX < left) return false;
        java.util.Set<java.util.UUID> assigned = new java.util.HashSet<>();
        ClientArmyCache.entries().forEach(entry -> assigned.addAll(entry.snapshot().groupIds()));
        java.util.Set<java.util.UUID> assignedCommanders = new java.util.HashSet<>();
        ClientArmyCache.entries().forEach(entry -> {
            if (entry.snapshot().commanderId() != null) assignedCommanders.add(entry.snapshot().commanderId());
        });
        int commanderY = 91;
        for (var commander : ClientGroupCatalog.commanders().stream().skip(commanderListOffset).limit(3).toList()) {
            if (mouseY >= commanderY - 3 && mouseY < commanderY + 17
                    && !assignedCommanders.contains(commander.commanderId())) {
                if (appointingArmy != null) {
                    CampaignNetwork.requestCommander(appointingArmy, commander.commanderId());
                    appointingArmy = null;
                    armyManagementMode = false;
                    return true;
                }
                pendingCommander = commander.commanderId();
                return true;
            }
            commanderY += 20;
        }
        int y = creationGroupStartY();
        int shown = 0;
        int maxRows = creationGroupRows();
        for (var group : ClientGroupCatalog.groups().stream().skip(groupListOffset).toList()) {
            if (shown >= maxRows) break;
            if (mouseY >= y - 3 && mouseY < y + 18 && !assigned.contains(group.groupId())) {
                if (!pendingGroups.add(group.groupId())) pendingGroups.remove(group.groupId());
                return true;
            }
            y += 22;
            shown++;
        }
        return true;
    }

    private void renderArmyPanel(GuiGraphics graphics, int left, CampaignArmySnapshot army) {
        if (selectedArmy == null) {
            graphics.drawString(font, font.plainSubstrByWidth(army.armyName().toUpperCase(), PANEL_WIDTH - 28),
                    left + 14, 64, 0xFFFFDDA0, false);
        }
        int y = 112;
        graphics.drawString(font, "ARMY STATUS", left + 14, y, 0xFF8D744B, false);
        y += 17;
        y = statRow(graphics, left, y, "State", stateLabel(army));
        y = statRow(graphics, left, y, "Commander", commanderName(army));
        y = statRow(graphics, left, y, "Strength", army.totalSoldiers() + " soldiers");
        y = statRow(graphics, left, y, "Groups", Integer.toString(army.groupIds().size()));
        y = statRow(graphics, left, y, "Supplies", Integer.toString(army.foodSupplies()));
        y = statRow(graphics, left, y, "Fatigue", String.format("%.1f", army.fatigue()));
        y += 7;
        graphics.drawString(font, "CONSTITUENT GROUPS", left + 14, y, 0xFF8D744B, false);
        y += 16;
        for (String group : groupNameList(army)) {
            if (y > height - 72) break;
            graphics.fill(left + 12, y - 3, width - 12, y + 14, 0x55303842);
            graphics.drawString(font, font.plainSubstrByWidth(group, PANEL_WIDTH - 36), left + 17, y, 0xFFE7E9EC, false);
            y += 20;
        }
    }

    private String commanderName(CampaignArmySnapshot army) {
        if (army.commanderId() == null || army.commanderVisual() == null) return "Leaderless";
        return army.commanderAvailable() ? army.commanderVisual().displayName()
                : "Leaderless (" + army.commanderVisual().displayName() + ")";
    }

    private static String stateLabel(CampaignArmySnapshot army) {
        if (!army.commanderAvailable()) return "Leaderless";
        return switch (army.state()) {
            case RETREATING -> "Preparing to move";
            case MOVING -> army.movementPath().isEmpty() ? "Awaiting orders" : "Marching";
            case DEPLOYING -> "Deploying groups";
            case DEPLOYED -> "Deployed";
            case STATIONARY -> "Stationary";
        };
    }

    private static String transitionText(CampaignArmySnapshot army) {
        int seconds = Math.max(0, (army.transitionTicksRemaining() + 19) / 20);
        return (army.state() == com.lastpagans.campaignmap.api.army.ArmyState.RETREATING
                ? "PACKING" : "DEPLOYING") + " • " + seconds + "s";
    }

    private void syncArmyControls() {
        hideArmyControls();
        createArmyButton.visible = armyManagementMode && appointingArmy == null;
        createArmyButton.active = pendingCommander != null && !pendingGroups.isEmpty();
        createArmyButton.setMessage(Component.literal(pendingCommander == null ? "SELECT COMMANDER"
                : pendingGroups.isEmpty() ? "SELECT GROUPS" : "CREATE ARMY (" + pendingGroups.size() + ")"));
        if (armyManagementMode || selectedArmy == null || minecraft.player == null) return;
        ClientArmyCache.Entry entry = ClientArmyCache.get(selectedArmy).orElse(null);
        if (entry == null || !entry.snapshot().ownerId().equals(minecraft.player.getUUID())) return;
        CampaignArmySnapshot army = entry.snapshot();
        armyNameInput.visible = true;
        renameButton.visible = true;
        flagButton.visible = true;
        armyActionButton.visible = true;
        if (!army.armyId().equals(editedArmy)) {
            editedArmy = army.armyId();
            armyNameInput.setValue(army.armyName());
        }
        if (!army.commanderAvailable()) {
            armyActionButton.active = true;
            armyActionButton.setMessage(Component.literal("APPOINT A COMMANDER"));
            return;
        }
        switch (army.state()) {
            case DEPLOYED, STATIONARY -> {
                armyActionButton.active = true;
                armyActionButton.setMessage(Component.literal("PREPARE TO MOVE"));
            }
            case RETREATING, DEPLOYING -> {
                armyActionButton.active = false;
                armyActionButton.setMessage(Component.literal(transitionText(army)));
            }
            case MOVING -> {
                armyActionButton.active = true;
                armyActionButton.setMessage(Component.literal(army.movementPath().isEmpty()
                        ? "DEPLOY HERE" : "HALT AND DEPLOY HERE"));
            }
        }
    }

    private void hideArmyControls() {
        if (armyNameInput != null) armyNameInput.visible = false;
        if (renameButton != null) renameButton.visible = false;
        if (flagButton != null) flagButton.visible = false;
        if (armyActionButton != null) armyActionButton.visible = false;
        if (createArmyButton != null) createArmyButton.visible = false;
    }

    private void renameSelectedArmy() {
        if (selectedArmy != null && !armyNameInput.getValue().isBlank())
            CampaignNetwork.requestArmyRename(selectedArmy, armyNameInput.getValue());
    }

    private void chooseSelectedFlag() {
        if (selectedArmy != null) CampaignNetwork.requestFlagEditor(selectedArmy);
    }

    private void runArmyAction() {
        if (selectedArmy == null) return;
        ClientArmyCache.get(selectedArmy).ifPresent(entry -> {
            if (!entry.snapshot().commanderAvailable()) {
                appointingArmy = selectedArmy;
                armyManagementMode = true;
                commanderListOffset = 0;
                CampaignNetwork.requestGroupCatalog();
                return;
            }
            switch (entry.snapshot().state()) {
                case DEPLOYED, STATIONARY -> CampaignNetwork.requestMovementMode(selectedArmy);
                case MOVING -> CampaignNetwork.requestDeployment(selectedArmy);
                default -> { }
            }
        });
    }

    private void createSelectedArmy() {
        if (pendingCommander == null || pendingGroups.isEmpty()) return;
        String name = ClientGroupCatalog.groups().stream().filter(group -> pendingGroups.contains(group.groupId()))
                .findFirst().map(group -> group.name() + " Army").orElse("New Army");
        CampaignNetwork.requestArmyCreation(name, pendingCommander, java.util.List.copyOf(pendingGroups));
        pendingGroups.clear();
        pendingCommander = null;
        armyManagementMode = false;
    }

    private java.util.List<String> groupNameList(CampaignArmySnapshot army) {
        return army.groupIds().stream().map(id -> ClientGroupCatalog.groups().stream()
                .filter(group -> group.groupId().equals(id))
                .map(group -> group.name() + " (" + group.soldierCount() + ")")
                .findFirst().orElse("Group (offline)"))
                .toList();
    }

    private int statRow(GuiGraphics graphics, int left, int y, String label, String value) {
        graphics.drawString(font, label, left + 14, y, 0xFF9EA7B3, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, 142), left + 102, y, 0xFFF1F2F3, false);
        return y + 17;
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
        hoveredArmy = bannerRenderer.pick(camera, mouseX, mouseY);
        ProvinceDefinition province = provinceAtScreen(mouseX, mouseY);
        ResourceLocation id = province == null ? null : province.id();
        if (!Objects.equals(id, hoveredProvince)) hoveredProvince = id;
    }

    @Nullable
    private ProvinceDefinition provinceAtScreen(double mouseX, double mouseY) {
        if (!camera.insideViewport(mouseX, mouseY)) return null;
        CampaignCamera3D.MapPoint point = camera.screenToMap(mouseX, mouseY);
        if (point == null) return null;
        int pixelX = (int) Math.floor(point.x());
        int pixelY = (int) Math.floor(point.y());
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
        ProvincePixelMap pixelMap = registry.pixelMap();
        for (int y = 0; y < pixelMap.height(); y++) {
            for (int x = 0; x < pixelMap.width(); x++) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleArmyPanelClick(mouseX, mouseY)) return true;
            if (hoveredArmy != null) {
                selectedArmy = hoveredArmy;
                selectedProvince = null;
                return true;
            }
            ProvinceDefinition province = provinceAtScreen(mouseX, mouseY);
            selectedProvince = province == null ? null : province.id();
            selectedArmy = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            dragging = true;
            dragDistance = 0.0;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            orbiting = true;
            dragDistance = 0.0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            camera.pan(mouseX - dragX, mouseY - dragY, mouseX, mouseY);
            dragDistance += Math.hypot(dragX, dragY);
            return true;
        }
        if (orbiting && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            camera.orbit(dragX, dragY);
            dragDistance += Math.hypot(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (dragging && dragDistance < 4.0 && selectedArmy != null) issueMovementOrder(mouseX, mouseY);
            dragging = false;
            camera.finishGesture();
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            orbiting = false;
            camera.finishGesture();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (armyManagementMode && mouseX >= width - PANEL_WIDTH) {
            if (appointingArmy != null || mouseY < creationGroupStartY()) {
                int maxOffset = Math.max(0, ClientGroupCatalog.commanders().size() - 3);
                commanderListOffset = Math.max(0, Math.min(maxOffset,
                        commanderListOffset + (amount < 0 ? 1 : -1)));
                return true;
            }
            int maxRows = creationGroupRows();
            int maxOffset = Math.max(0, ClientGroupCatalog.groups().size() - maxRows);
            groupListOffset = Math.max(0, Math.min(maxOffset, groupListOffset + (amount < 0 ? 1 : -1)));
            return true;
        }
        camera.zoom(amount);
        return true;
    }

    private void issueMovementOrder(double mouseX, double mouseY) {
        if (minecraft.player == null || !camera.insideViewport(mouseX, mouseY)) return;
        ClientArmyCache.Entry entry = ClientArmyCache.get(selectedArmy).orElse(null);
        if (entry == null || !entry.snapshot().ownerId().equals(minecraft.player.getUUID())) return;
        CampaignCamera3D.MapPoint point = camera.screenToMap(mouseX, mouseY);
        if (point == null || !registry.projection().isPixelInsideMap((int) point.x(), (int) point.y())) return;
        MapProjection.WorldPoint world = registry.projection().pixelToWorld(point.x(), point.y());
        CampaignNetwork.requestArmyMove(selectedArmy, world.x(), world.z());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (armyNameInput != null && armyNameInput.visible && armyNameInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                renameSelectedArmy();
                armyNameInput.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_A) {
            armyManagementMode = !armyManagementMode;
            if (armyManagementMode) {
                groupListOffset = 0;
                commanderListOffset = 0;
                pendingCommander = null;
                appointingArmy = null;
                CampaignNetwork.requestGroupCatalog();
            }
            return true;
        }
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

    private int creationGroupStartY() {
        return 112 + Math.min(3, ClientGroupCatalog.commanders().size()) * 20;
    }

    private int creationGroupRows() {
        return Math.max(1, (height - creationGroupStartY() - 70) / 22);
    }

    private void renderCommanderAppointmentPanel(GuiGraphics graphics, int left) {
        graphics.drawString(font, "APPOINT COMMANDER", left + 14, 58, 0xFFFFDDA0, false);
        graphics.drawString(font, "Select an available Recruits commander", left + 14, 74, 0xFFAEB8C4, false);
        int y = 91;
        java.util.Set<java.util.UUID> assigned = new java.util.HashSet<>();
        ClientArmyCache.entries().forEach(entry -> {
            if (entry.snapshot().commanderId() != null) assigned.add(entry.snapshot().commanderId());
        });
        for (var commander : ClientGroupCatalog.commanders().stream().skip(commanderListOffset).limit(3).toList()) {
            boolean unavailable = assigned.contains(commander.commanderId());
            graphics.fill(left + 10, y - 3, width - 10, y + 15, unavailable ? 0x55343434 : 0x88303842);
            graphics.drawString(font, font.plainSubstrByWidth(commander.name(), PANEL_WIDTH - 30),
                    left + 14, y, unavailable ? 0xFF777777 : 0xFFE7E9EC, false);
            y += 20;
        }
        if (ClientGroupCatalog.commanders().isEmpty())
            graphics.drawString(font, "No available Recruits commanders", left + 14, y, 0xFFFF8888, false);
    }

    @Override
    public void removed() {
        if (overlayLocation != null) Minecraft.getInstance().getTextureManager().release(overlayLocation);
        overlayLocation = null;
        overlayTexture = null;
    }

    private enum Border { NONE, PROVINCE, POLITICAL }
}
