package com.lastpagans.campaignmap.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ArmyCreateRequestPacket(String name, List<UUID> groupIds) {
    public static void encode(ArmyCreateRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.name, 64);
        buffer.writeVarInt(Math.min(128, packet.groupIds.size()));
        packet.groupIds.stream().limit(128).forEach(buffer::writeUUID);
    }

    public static ArmyCreateRequestPacket decode(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(64);
        int count = buffer.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid group count " + count);
        List<UUID> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) groups.add(buffer.readUUID());
        return new ArmyCreateRequestPacket(name, List.copyOf(groups));
    }
}
