package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.api.army.CampaignCommanderSummary;
import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Dedicated two-step screen for selecting an owned commander and that commander's groups. */
public final class ArmyFormationScreen extends Screen {
    private static final int PANEL_WIDTH = 620;
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final Set<UUID> selectedGroups = new LinkedHashSet<>();
    @Nullable private UUID selectedCommander;
    private EditBox nameInput;
    private Button formButton;
    private int commanderOffset;
    private int groupOffset;

    public ArmyFormationScreen(Screen parent) {
        super(Component.literal("Form Army"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CampaignNetwork.requestGroupCatalog();
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        nameInput = new EditBox(font, left + 24, top + 48, panelWidth - 48, 20,
                Component.literal("Army name"));
        nameInput.setMaxLength(64);
        nameInput.setValue("New Army");
        addRenderableWidget(nameInput);
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> onClose())
                .bounds(left + 24, top + panelHeight() - 42, 110, 22).build());
        formButton = addRenderableWidget(Button.builder(Component.literal("FORM ARMY"), button -> formArmy())
                .bounds(left + panelWidth - 174, top + panelHeight() - 42, 150, 22).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE8101317);
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF21A1E24);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFF8D744B);
        graphics.drawCenteredString(font, title, width / 2, top + 15, 0xFFFFDDA0);
        graphics.drawString(font, "Army name", left + 24, top + 36, 0xFF9EA7B3, false);

        int listTop = top + 94;
        int midpoint = left + panelWidth / 2;
        graphics.drawString(font, "1. OWNED COMMANDER", left + 24, listTop - 18, 0xFFFFDDA0, false);
        graphics.drawString(font, "2. GROUPS TO COMMAND", midpoint + 12, listTop - 18, 0xFFFFDDA0, false);
        graphics.fill(midpoint, listTop - 24, midpoint + 1, top + panelHeight - 55, 0xFF393F47);

        Set<UUID> assignedCommanders = assignedCommanders();
        Set<UUID> assignedGroups = assignedGroups();
        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int index = commanderOffset + row;
            if (index >= ClientGroupCatalog.commanders().size()) break;
            CampaignCommanderSummary commander = ClientGroupCatalog.commanders().get(index);
            boolean assigned = assignedCommanders.contains(commander.commanderId());
            boolean selected = commander.commanderId().equals(selectedCommander);
            drawRow(graphics, left + 18, listTop + row * ROW_HEIGHT, panelWidth / 2 - 30,
                    commander.name(), assigned, selected);
        }
        for (int row = 0; row < rows; row++) {
            int index = groupOffset + row;
            if (index >= ClientGroupCatalog.groups().size()) break;
            CampaignGroupSummary group = ClientGroupCatalog.groups().get(index);
            boolean assigned = assignedGroups.contains(group.groupId());
            boolean selected = selectedGroups.contains(group.groupId());
            drawRow(graphics, midpoint + 8, listTop + row * ROW_HEIGHT, panelWidth / 2 - 26,
                    group.name() + "  [" + group.soldierCount() + "]", assigned, selected);
        }

        if (ClientGroupCatalog.commanders().isEmpty())
            graphics.drawString(font, "No available owned commanders", left + 24, listTop, 0xFFFF8888, false);
        if (ClientGroupCatalog.groups().isEmpty())
            graphics.drawString(font, "No available owned groups", midpoint + 14, listTop, 0xFFFF8888, false);
        if (ClientGroupCatalog.commanders().size() > rows || ClientGroupCatalog.groups().size() > rows)
            graphics.drawCenteredString(font, "Move over a column and use the wheel to browse",
                    width / 2, top + panelHeight - 59, 0xFF8F98A3);

        formButton.active = selectedCommander != null && !selectedGroups.isEmpty()
                && !nameInput.getValue().isBlank();
        formButton.setMessage(Component.literal(selectedCommander == null ? "SELECT COMMANDER"
                : selectedGroups.isEmpty() ? "SELECT GROUPS" : "FORM ARMY (" + selectedGroups.size() + ")"));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int rowWidth, String text,
                         boolean assigned, boolean selected) {
        graphics.fill(x, y, x + rowWidth, y + 18,
                assigned ? 0x55343434 : selected ? 0xCC31596A : 0xAA303842);
        int color = assigned ? 0xFF6F747B : selected ? 0xFF8DEAFF : 0xFFE7E9EC;
        String label = assigned ? text + "  (assigned)" : text;
        graphics.drawString(font, font.plainSubstrByWidth(label, rowWidth - 10), x + 5, y + 5, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int left = panelLeft();
        int top = panelTop();
        int listTop = top + 94;
        int panelWidth = panelWidth();
        int midpoint = left + panelWidth / 2;
        int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
        if (mouseY < listTop || row < 0 || row >= visibleRows()) return false;
        if (mouseX >= left + 18 && mouseX < midpoint - 12) {
            int index = commanderOffset + row;
            if (index < ClientGroupCatalog.commanders().size()) {
                CampaignCommanderSummary commander = ClientGroupCatalog.commanders().get(index);
                if (!assignedCommanders().contains(commander.commanderId()))
                    selectedCommander = commander.commanderId();
            }
            return true;
        }
        if (mouseX >= midpoint + 8 && mouseX < left + panelWidth - 18) {
            int index = groupOffset + row;
            if (index < ClientGroupCatalog.groups().size()) {
                CampaignGroupSummary group = ClientGroupCatalog.groups().get(index);
                if (selectedCommander != null && !assignedGroups().contains(group.groupId())) {
                    if (!selectedGroups.add(group.groupId())) selectedGroups.remove(group.groupId());
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int direction = amount < 0.0D ? 1 : -1;
        int midpoint = panelLeft() + panelWidth() / 2;
        if (mouseX < midpoint) commanderOffset = scroll(commanderOffset, direction,
                ClientGroupCatalog.commanders().size());
        else groupOffset = scroll(groupOffset, direction, ClientGroupCatalog.groups().size());
        return true;
    }

    private int scroll(int current, int direction, int size) {
        return Math.max(0, Math.min(Math.max(0, size - visibleRows()), current + direction));
    }

    private void formArmy() {
        if (selectedCommander == null || selectedGroups.isEmpty() || nameInput.getValue().isBlank()) return;
        CampaignNetwork.requestArmyCreation(nameInput.getValue(), selectedCommander,
                java.util.List.copyOf(selectedGroups));
        minecraft.setScreen(parent);
    }

    private Set<UUID> assignedCommanders() {
        Set<UUID> result = new HashSet<>();
        UUID player = minecraft.player == null ? null : minecraft.player.getUUID();
        ClientArmyCache.entries().stream().map(ClientArmyCache.Entry::snapshot)
                .filter(army -> player != null && army.ownerId().equals(player) && army.commanderId() != null)
                .forEach(army -> result.add(army.commanderId()));
        return result;
    }

    private Set<UUID> assignedGroups() {
        Set<UUID> result = new HashSet<>();
        UUID player = minecraft.player == null ? null : minecraft.player.getUUID();
        ClientArmyCache.entries().stream().map(ClientArmyCache.Entry::snapshot)
                .filter(army -> player != null && army.ownerId().equals(player))
                .forEach(army -> result.addAll(army.groupIds()));
        return result;
    }

    private int panelWidth() { return Math.max(360, Math.min(PANEL_WIDTH, width - 24)); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return Math.max(12, (height - panelHeight()) / 2); }
    private int panelHeight() { return Math.min(440, height - 24); }
    private int visibleRows() { return Math.max(3, (panelHeight() - 168) / ROW_HEIGHT); }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
