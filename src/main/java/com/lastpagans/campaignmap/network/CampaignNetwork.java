package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.client.ClientOwnershipCache;
import com.lastpagans.campaignmap.data.CampaignOwnershipData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

public final class CampaignNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CampaignMapMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private CampaignNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(OwnershipSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OwnershipSyncPacket::encode)
                .decoder(OwnershipSyncPacket::decode)
                .consumerMainThread(CampaignNetwork::handleOwnership)
                .add();
    }

    private static void handleOwnership(OwnershipSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientOwnershipCache.apply(packet));
    }

    public static void sendFull(ServerPlayer player) {
        CampaignOwnershipData data = CampaignOwnershipData.get(player.serverLevel());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OwnershipSyncPacket(true, List.copyOf(data.states())));
    }

    public static void sendUpdate(ProvinceOwnerState state) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new OwnershipSyncPacket(false, List.of(state)));
    }
}
