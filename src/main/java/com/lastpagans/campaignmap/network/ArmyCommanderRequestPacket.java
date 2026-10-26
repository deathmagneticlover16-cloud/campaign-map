package com.lastpagans.campaignmap.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ArmyCommanderRequestPacket(UUID armyId, UUID commanderId) {
    public static void encode(ArmyCommanderRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.armyId);
        buffer.writeUUID(packet.commanderId);
    }

    public static ArmyCommanderRequestPacket decode(FriendlyByteBuf buffer) {
        return new ArmyCommanderRequestPacket(buffer.readUUID(), buffer.readUUID());
    }
}
