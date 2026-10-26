package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.api.army.CampaignGroupSummary;
import com.lastpagans.campaignmap.api.army.CampaignCommanderSummary;
import com.lastpagans.campaignmap.network.GroupCatalogSyncPacket;

import java.util.List;

public final class ClientGroupCatalog {
    private static List<CampaignGroupSummary> groups = List.of();
    private static List<CampaignCommanderSummary> commanders = List.of();

    private ClientGroupCatalog() {}

    public static void apply(GroupCatalogSyncPacket packet) {
        groups = List.copyOf(packet.groups());
        commanders = List.copyOf(packet.commanders());
    }
    public static List<CampaignGroupSummary> groups() { return groups; }
    public static List<CampaignCommanderSummary> commanders() { return commanders; }
}
