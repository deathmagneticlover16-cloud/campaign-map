package com.lastpagans.campaignmap.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ArmyMoveRequestPacket(UUID armyId, double worldX, double worldZ) {
    public static void encode(ArmyMoveRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.armyId);
        buffer.writeDouble(packet.worldX);
        buffer.writeDouble(packet.worldZ);
    }

    public static ArmyMoveRequestPacket decode(FriendlyByteBuf buffer) {
        return new ArmyMoveRequestPacket(buffer.readUUID(), buffer.readDouble(), buffer.readDouble());
    }
}
