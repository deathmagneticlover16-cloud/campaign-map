package com.lastpagans.campaignmap;

import com.lastpagans.campaignmap.client.CampaignMapClient;
import com.lastpagans.campaignmap.army.CampaignArmyData;
import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import com.lastpagans.campaignmap.command.CampaignMapCommands;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(CampaignMapMod.MOD_ID)
public final class CampaignMapMod {
    public static final String MOD_ID = "campaignmap";
    public static final Logger LOGGER = LogUtils.getLogger();
    private int armySyncTicker;

    public CampaignMapMod() {
        ProvinceRegistry.get();
        CampaignNetwork.register();
        if (ModList.get().isLoaded("recruits")) {
            registerRecruitsIntegration();
        }
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::playerJoined);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        MinecraftForge.EVENT_BUS.addListener(this::entityJoined);
        MinecraftForge.EVENT_BUS.addListener(this::entityDied);
        DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> CampaignMapClient.register(FMLJavaModLoadingContext.get().getModEventBus()));
    }

    private void entityJoined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            CampaignGroupProviders.onEntityLoaded(level, event.getEntity(), CampaignArmyData.get(level));
    }

    private void entityDied(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        CampaignArmyData data = CampaignArmyData.get(level);
        if (data.hasCommander(event.getEntity().getUUID()))
            data.markCommanderUnavailable(level, event.getEntity().getUUID()).ifPresent(CampaignNetwork::sendArmyUpdate);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CampaignMapCommands.register(event.getDispatcher());
    }

    private void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CampaignNetwork.sendFull(player);
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var level = server.overworld();
        var changed = CampaignArmyData.get(level).tick(level);
        armySyncTicker++;
        boolean periodicSync = armySyncTicker >= 5;
        if (periodicSync) {
            armySyncTicker = 0;
        }
        changed.stream()
                .filter(army -> periodicSync || army.movementPath().isEmpty()
                        || army.state() == ArmyState.STATIONARY)
                .forEach(CampaignNetwork::sendArmyUpdate);
    }

    private static void registerRecruitsIntegration() {
        try {
            var type = Class.forName("com.lastpagans.campaignmap.army.RecruitsCampaignGroupProvider");
            CampaignGroupProviders.register((com.lastpagans.campaignmap.api.army.CampaignGroupProvider)
                    type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.error("Recruits is installed, but its campaign-map integration could not start", error);
        }
    }
}
