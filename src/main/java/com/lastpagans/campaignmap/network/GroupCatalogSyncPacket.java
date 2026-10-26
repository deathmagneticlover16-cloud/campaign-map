package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record GroupCatalogSyncPacket(List<CampaignGroupSummary> groups) {
    public static void encode(GroupCatalogSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.groups.size());
        packet.groups.forEach(group -> {
            buffer.writeUUID(group.groupId());
            buffer.writeUtf(group.name(), 64);
            buffer.writeVarInt(group.soldierCount());
        });
    }

    public static GroupCatalogSyncPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid group catalog size " + count);
        List<CampaignGroupSummary> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            groups.add(new CampaignGroupSummary(buffer.readUUID(), buffer.readUtf(64), buffer.readVarInt()));
        }
        return new GroupCatalogSyncPacket(List.copyOf(groups));
    }
}
