package com.lastpagans.campaignmap.network;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
public record ArmyMovementModeRequestPacket(UUID armyId) {
    public static void encode(ArmyMovementModeRequestPacket packet, FriendlyByteBuf buffer){ buffer.writeUUID(packet.armyId); }
    public static ArmyMovementModeRequestPacket decode(FriendlyByteBuf buffer){ return new ArmyMovementModeRequestPacket(buffer.readUUID()); }
}
