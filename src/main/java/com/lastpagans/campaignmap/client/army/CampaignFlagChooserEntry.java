package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/** Keeps optional Immersive Paintings classes out of Campaign Map's normal client class path. */
public final class CampaignFlagChooserEntry {
    private CampaignFlagChooserEntry() {}

    public static void open(UUID armyId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModList.get().isLoaded("immersive_paintings")) {
            message(minecraft, "Immersive Paintings 0.6.13 is required to choose an army flag.");
            return;
        }
        try {
            Class<?> screen = Class.forName("com.lastpagans.campaignmap.client.army.CampaignFlagChooserScreen");
            screen.getMethod("open", UUID.class).invoke(null, armyId);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : error;
            CampaignMapMod.LOGGER.error("Immersive Paintings 0.6.13 flag browser is incompatible", cause);
            message(minecraft, "Immersive Paintings is installed, but its flag browser is incompatible.");
        }
    }

    private static void message(Minecraft minecraft, String text) {
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(text), false);
    }
}
