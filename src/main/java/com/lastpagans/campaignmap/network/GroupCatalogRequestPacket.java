package com.lastpagans.campaignmap.network;
import net.minecraft.network.FriendlyByteBuf;
public record GroupCatalogRequestPacket() {
    public static void encode(GroupCatalogRequestPacket packet, FriendlyByteBuf buffer) {}
    public static GroupCatalogRequestPacket decode(FriendlyByteBuf buffer) { return new GroupCatalogRequestPacket(); }
}
