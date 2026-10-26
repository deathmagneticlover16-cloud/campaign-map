package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.lastpagans.campaignmap.api.army.CampaignCommanderSummary;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record GroupCatalogSyncPacket(List<CampaignGroupSummary> groups, List<CampaignCommanderSummary> commanders) {
    public static void encode(GroupCatalogSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.groups.size());
        packet.groups.forEach(group -> {
            buffer.writeUUID(group.groupId());
            buffer.writeUtf(group.name(), 64);
            buffer.writeVarInt(group.soldierCount());
        });
        buffer.writeVarInt(packet.commanders.size());
        packet.commanders.forEach(commander -> {
            buffer.writeUUID(commander.commanderId());
            buffer.writeUtf(commander.name(), 128);
        });
    }

    public static GroupCatalogSyncPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid group catalog size " + count);
        List<CampaignGroupSummary> groups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            groups.add(new CampaignGroupSummary(buffer.readUUID(), buffer.readUtf(64), buffer.readVarInt()));
        }
        int commanderCount = buffer.readVarInt();
        if (commanderCount < 0 || commanderCount > 1024)
            throw new IllegalArgumentException("Invalid commander catalog size " + commanderCount);
        List<CampaignCommanderSummary> commanders = new ArrayList<>(commanderCount);
        for (int i = 0; i < commanderCount; i++)
            commanders.add(new CampaignCommanderSummary(buffer.readUUID(), buffer.readUtf(128)));
        return new GroupCatalogSyncPacket(List.copyOf(groups), List.copyOf(commanders));
    }
}
