package com.lastpagans.campaignmap.network;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
public record ArmyDeployRequestPacket(UUID armyId) {
    public static void encode(ArmyDeployRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.armyId); }
    public static ArmyDeployRequestPacket decode(FriendlyByteBuf buffer) { return new ArmyDeployRequestPacket(buffer.readUUID()); }
}
