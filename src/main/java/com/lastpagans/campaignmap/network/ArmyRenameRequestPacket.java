package com.lastpagans.campaignmap.network;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
public record ArmyRenameRequestPacket(UUID armyId, String name) {
    public static void encode(ArmyRenameRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.armyId); buffer.writeUtf(packet.name, 64); }
    public static ArmyRenameRequestPacket decode(FriendlyByteBuf buffer) { return new ArmyRenameRequestPacket(buffer.readUUID(), buffer.readUtf(64)); }
}
