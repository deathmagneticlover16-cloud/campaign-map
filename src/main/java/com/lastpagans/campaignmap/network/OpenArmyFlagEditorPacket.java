package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.client.army.CampaignFlagChooserScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record OpenArmyFlagEditorPacket(UUID armyId) {
    public static void encode(OpenArmyFlagEditorPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.armyId); }
    public static OpenArmyFlagEditorPacket decode(FriendlyByteBuf buffer) { return new OpenArmyFlagEditorPacket(buffer.readUUID()); }
    public static void handle(OpenArmyFlagEditorPacket packet, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CampaignFlagChooserScreen.open(packet.armyId)));
        supplier.get().setPacketHandled(true);
    }
}
