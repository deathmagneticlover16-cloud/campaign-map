package com.lastpagans.campaignmap;

import com.lastpagans.campaignmap.client.CampaignMapClient;
import com.lastpagans.campaignmap.command.CampaignMapCommands;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CampaignMapMod.MOD_ID)
public final class CampaignMapMod {
    public static final String MOD_ID = "campaignmap";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CampaignMapMod() {
        ProvinceRegistry.get();
        CampaignNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::playerJoined);
        DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> CampaignMapClient.register(FMLJavaModLoadingContext.get().getModEventBus()));
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CampaignMapCommands.register(event.getDispatcher());
    }

    private void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CampaignNetwork.sendFull(player);
    }
}
