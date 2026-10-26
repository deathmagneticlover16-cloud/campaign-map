package com.lastpagans.campaignmap.network;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
public record ArmyFlagEditorRequestPacket(UUID armyId) {
    public static void encode(ArmyFlagEditorRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.armyId); }
    public static ArmyFlagEditorRequestPacket decode(FriendlyByteBuf buffer) { return new ArmyFlagEditorRequestPacket(buffer.readUUID()); }
}
