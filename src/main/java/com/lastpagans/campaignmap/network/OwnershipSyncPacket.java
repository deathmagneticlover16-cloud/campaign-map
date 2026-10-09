package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record OwnershipSyncPacket(boolean replaceAll, List<ProvinceOwnerState> states) {
    public static void encode(OwnershipSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.replaceAll);
        buffer.writeVarInt(packet.states.size());
        for (ProvinceOwnerState state : packet.states) {
            buffer.writeResourceLocation(state.provinceId());
            writeIdentity(buffer, state.owner());
            writeIdentity(buffer, state.controller());
        }
    }

    public static OwnershipSyncPacket decode(FriendlyByteBuf buffer) {
        boolean replaceAll = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > 10_000) throw new IllegalArgumentException("Invalid ownership state count " + count);
        List<ProvinceOwnerState> states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            states.add(new ProvinceOwnerState(id, readIdentity(buffer), readIdentity(buffer)));
        }
        return new OwnershipSyncPacket(replaceAll, states);
    }

    private static void writeIdentity(FriendlyByteBuf buffer, @Nullable ProvinceOwnerState.PoliticalIdentity identity) {
        buffer.writeBoolean(identity != null);
        if (identity != null) {
            buffer.writeUtf(identity.id(), 128);
            buffer.writeInt(identity.color() & 0xFFFFFF);
        }
    }

    @Nullable
    private static ProvinceOwnerState.PoliticalIdentity readIdentity(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        return new ProvinceOwnerState.PoliticalIdentity(buffer.readUtf(128), buffer.readInt() & 0xFFFFFF);
    }
}
