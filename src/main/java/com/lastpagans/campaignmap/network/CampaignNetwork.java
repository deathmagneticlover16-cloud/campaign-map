package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.api.army.CampaignGroupProviders;
import com.lastpagans.campaignmap.army.CampaignArmyData;
import com.lastpagans.campaignmap.client.ClientOwnershipCache;
import com.lastpagans.campaignmap.client.army.ClientArmyCache;
import com.lastpagans.campaignmap.client.army.ClientGroupCatalog;
import com.lastpagans.campaignmap.data.CampaignOwnershipData;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

public final class CampaignNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CampaignMapMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private CampaignNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(OwnershipSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OwnershipSyncPacket::encode)
                .decoder(OwnershipSyncPacket::decode)
                .consumerMainThread(CampaignNetwork::handleOwnership)
                .add();
        CHANNEL.messageBuilder(ArmySyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ArmySyncPacket::encode)
                .decoder(ArmySyncPacket::decode)
                .consumerMainThread(CampaignNetwork::handleArmySync)
                .add();
        CHANNEL.messageBuilder(ArmyMoveRequestPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ArmyMoveRequestPacket::encode)
                .decoder(ArmyMoveRequestPacket::decode)
                .consumerMainThread(CampaignNetwork::handleArmyMove)
                .add();
        CHANNEL.messageBuilder(GroupCatalogSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GroupCatalogSyncPacket::encode)
                .decoder(GroupCatalogSyncPacket::decode)
                .consumerMainThread(CampaignNetwork::handleGroupCatalog)
                .add();
        CHANNEL.messageBuilder(ArmyCreateRequestPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ArmyCreateRequestPacket::encode)
                .decoder(ArmyCreateRequestPacket::decode)
                .consumerMainThread(CampaignNetwork::handleArmyCreate)
                .add();
    }

    private static void handleOwnership(OwnershipSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientOwnershipCache.apply(packet));
    }

    private static void handleArmySync(ArmySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientArmyCache.apply(packet));
    }

    private static void handleGroupCatalog(GroupCatalogSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGroupCatalog.apply(packet));
    }

    private static void handleArmyMove(ArmyMoveRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null && Double.isFinite(packet.worldX()) && Double.isFinite(packet.worldZ())
                && ProvinceRegistry.get().projection().isWorldInsideMap(packet.worldX(), packet.worldZ())) {
            CampaignArmyData data = CampaignArmyData.get(player.serverLevel());
            if (data.owns(packet.armyId(), player.getUUID())
                    && data.setMovementOrder(packet.armyId(), new Vec3(packet.worldX(), 0.0, packet.worldZ()))) {
                data.snapshot(player.serverLevel(), packet.armyId()).ifPresent(CampaignNetwork::sendArmyUpdate);
            }
        }
        context.setPacketHandled(true);
    }

    private static void handleArmyCreate(ArmyCreateRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null && !packet.groupIds().isEmpty() && packet.groupIds().size() <= 128) {
            CampaignArmyData data = CampaignArmyData.get(player.serverLevel());
            List<java.util.UUID> validGroups = packet.groupIds().stream().distinct()
                    .filter(group -> CampaignGroupProviders.hasProviderFor(player.serverLevel(), group))
                    .filter(group -> CampaignGroupProviders.canManage(player.serverLevel(), player.getUUID(), group))
                    .filter(group -> !data.isGroupAssigned(group))
                    .toList();
            if (!validGroups.isEmpty()) {
                double x = player.getX();
                double z = player.getZ();
                if (!ProvinceRegistry.get().projection().isWorldInsideMap(x, z)) {
                    var center = ProvinceRegistry.get().projection().pixelToWorld(
                            ProvinceRegistry.get().pixelMap().width() * 0.5,
                            ProvinceRegistry.get().pixelMap().height() * 0.5);
                    x = center.x();
                    z = center.z();
                }
                CampaignArmySnapshot army = data.create(player.serverLevel(), player.getUUID(), packet.name(),
                        player.getUUID(), new Vec3(x, 0.0, z));
                for (java.util.UUID group : validGroups) {
                    data.addGroup(player.serverLevel(), army.armyId(), player.getUUID(), group, false);
                }
                data.snapshot(player.serverLevel(), army.armyId()).ifPresent(CampaignNetwork::sendArmyUpdate);
            }
        }
        context.setPacketHandled(true);
    }

    public static void sendFull(ServerPlayer player) {
        CampaignOwnershipData data = CampaignOwnershipData.get(player.serverLevel());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OwnershipSyncPacket(true, List.copyOf(data.states())));
        CampaignArmyData armyData = CampaignArmyData.get(player.serverLevel());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ArmySyncPacket.full(armyData.snapshots(player.serverLevel())));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GroupCatalogSyncPacket(
                CampaignGroupProviders.ownedGroups(player.serverLevel(), player.getUUID())));
    }

    public static void sendUpdate(ProvinceOwnerState state) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new OwnershipSyncPacket(false, List.of(state)));
    }

    public static void sendArmyUpdate(CampaignArmySnapshot army) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), ArmySyncPacket.update(army));
    }

    public static void sendArmyRemoval(java.util.UUID armyId) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), ArmySyncPacket.remove(armyId));
    }

    public static void requestArmyMove(java.util.UUID armyId, double worldX, double worldZ) {
        CHANNEL.sendToServer(new ArmyMoveRequestPacket(armyId, worldX, worldZ));
    }

    public static void requestArmyCreation(String name, List<java.util.UUID> groupIds) {
        CHANNEL.sendToServer(new ArmyCreateRequestPacket(name, List.copyOf(groupIds)));
    }
}
