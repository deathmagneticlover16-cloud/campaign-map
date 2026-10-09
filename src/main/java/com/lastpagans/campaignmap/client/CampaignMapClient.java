package com.lastpagans.campaignmap.client;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class CampaignMapClient {
    private static final KeyMapping OPEN_MAP = new KeyMapping("key.campaignmap.open_map",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.campaignmap");

    private CampaignMapClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CampaignMapClient::registerKeys);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }

    @Mod.EventBusSubscriber(modid = CampaignMapMod.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (OPEN_MAP.consumeClick()) Minecraft.getInstance().setScreen(new CampaignMapScreen());
        }
    }
}
