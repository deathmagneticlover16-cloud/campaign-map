package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.lastpagans.campaignmap.network.GroupCatalogSyncPacket;

import java.util.List;

public final class ClientGroupCatalog {
    private static List<CampaignGroupSummary> groups = List.of();

    private ClientGroupCatalog() {}

    public static void apply(GroupCatalogSyncPacket packet) { groups = List.copyOf(packet.groups()); }
    public static List<CampaignGroupSummary> groups() { return groups; }
}
