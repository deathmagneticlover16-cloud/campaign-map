package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.network.CampaignNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Immersive Paintings catalog browser without linking its client classes on dedicated servers. */
public final class CampaignFlagChooserScreen extends Screen {
    private final UUID armyId;
    private final List<Entry> entries;
    private int page;
    private CampaignFlagChooserScreen(UUID armyId, List<Entry> entries) {
        super(Component.literal("Choose army flag")); this.armyId = armyId; this.entries = entries;
    }
    public static void open(UUID armyId) {
        Minecraft mc = Minecraft.getInstance();
        if (!ModList.get().isLoaded("immersive_paintings")) {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Immersive Paintings is required."), false);
            return;
        }
        mc.setScreen(new CampaignFlagChooserScreen(armyId, load()));
    }
    private static List<Entry> load() {
        List<Entry> result = new ArrayList<>();
        try {
            Class<?> manager = Class.forName("immersive_paintings.resources.ClientPaintingManager");
            Map<?, ?> paintings = (Map<?, ?>) manager.getMethod("getPaintings").invoke(null);
            Method getTexture = Arrays.stream(manager.getMethods()).filter(m -> m.getName().equals("getPaintingTexture")).findFirst().orElseThrow();
            Class<?> type = Class.forName("immersive_paintings.resources.Painting$Type");
            Object full = Enum.valueOf((Class) type, "FULL");
            for (var item : paintings.entrySet()) {
                if (!(item.getKey() instanceof ResourceLocation id)) continue;
                Object painting = item.getValue();
                Object texture = getTexture.invoke(null, id, full);
                Field textureId = texture.getClass().getField("textureIdentifier");
                int width = painting.getClass().getField("width").getInt(painting);
                int height = painting.getClass().getField("height").getInt(painting);
                result.add(new Entry(id, (ResourceLocation) textureId.get(texture), height <= 0 ? 1.5 : width / (double) height));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return List.copyOf(result);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        int columns = Math.max(1, (width - 32) / 84);
        int pageSize = columns * 5, start = page * pageSize, end = Math.min(entries.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            int local = i - start, x = 16 + (local % columns) * 84, y = 36 + (local / columns) * 70;
            graphics.blit(entries.get(i).texture, x, y, 72, 52, 0, 0, 1, 1, 1, 1);
            if (mouseX >= x && mouseX < x + 72 && mouseY >= y && mouseY < y + 52) graphics.renderOutline(x-1,y-1,74,54,0xFFFFFFFF);
        }
        if (entries.isEmpty()) graphics.drawCenteredString(font, "No Immersive Paintings were found.", width/2, 50, 0xFFFF8888);
        else graphics.drawCenteredString(font, "Mouse wheel: page " + (page + 1) + "/" + Math.max(1, (entries.size()+pageSize-1)/pageSize), width/2, height-18, 0xFFBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int columns = Math.max(1, (width - 32) / 84);
        int pageSize=columns*5,start=page*pageSize,end=Math.min(entries.size(),start+pageSize);
        for (int i=start;i<end;i++) {
            int local=i-start,x=16+(local%columns)*84,y=36+(local/columns)*70;
            if(mouseX>=x&&mouseX<x+72&&mouseY>=y&&mouseY<y+52){ Entry e=entries.get(i); CampaignNetwork.requestArmyFlag(armyId,e.id,e.aspect); onClose(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int pageSize=Math.max(1,(Math.max(1,(width-32)/84))*5);
        int pages=Math.max(1,(entries.size()+pageSize-1)/pageSize);
        page=Math.max(0,Math.min(pages-1,page+(amount<0?1:-1)));
        return true;
    }
    private record Entry(ResourceLocation id, ResourceLocation texture, double aspect) {}
}
