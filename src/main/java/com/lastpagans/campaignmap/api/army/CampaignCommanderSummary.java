package com.lastpagans.campaignmap.api.army;

import java.util.UUID;

/** A real, selectable Recruits commander. */
public record CampaignCommanderSummary(UUID commanderId, String name) {}
