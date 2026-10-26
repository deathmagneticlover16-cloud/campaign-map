package com.lastpagans.campaignmap.api.army;

import java.util.UUID;

public record CampaignGroupSummary(UUID groupId, String name, int soldierCount) {}
