package com.lastpagans.campaignmap.client;

import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.network.OwnershipSyncPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class ClientOwnershipCache {
    private static final Map<ResourceLocation, ProvinceOwnerState> STATES = new HashMap<>();
    private static long revision;

    private ClientOwnershipCache() {}

    public static void apply(OwnershipSyncPacket packet) {
        if (packet.replaceAll()) STATES.clear();
        for (ProvinceOwnerState state : packet.states()) {
            if (state.owner() == null && state.controller() == null) STATES.remove(state.provinceId());
            else STATES.put(state.provinceId(), state);
        }
        revision++;
    }

    public static ProvinceOwnerState state(ResourceLocation provinceId) {
        return STATES.getOrDefault(provinceId, new ProvinceOwnerState(provinceId, null, null));
    }

    public static long revision() {
        return revision;
    }
}
