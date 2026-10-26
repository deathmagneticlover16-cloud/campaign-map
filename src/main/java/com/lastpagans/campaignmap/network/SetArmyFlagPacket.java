package com.lastpagans.campaignmap.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

public record SetArmyFlagPacket(UUID armyId, String paintingId, double aspect) {
    public static void encode(SetArmyFlagPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.armyId); buffer.writeUtf(packet.paintingId, 256); buffer.writeDouble(packet.aspect);
    }
    public static SetArmyFlagPacket decode(FriendlyByteBuf buffer) {
        return new SetArmyFlagPacket(buffer.readUUID(), buffer.readUtf(256), buffer.readDouble());
    }
}
