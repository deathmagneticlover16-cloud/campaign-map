package com.lastpagans.campaignmap.network;

import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ArmySyncPacket(boolean replaceAll, List<CampaignArmySnapshot> armies, List<UUID> removedArmyIds) {
    public static ArmySyncPacket full(List<CampaignArmySnapshot> armies) {
        return new ArmySyncPacket(true, armies, List.of());
    }

    public static ArmySyncPacket update(CampaignArmySnapshot army) {
        return new ArmySyncPacket(false, List.of(army), List.of());
    }

    public static ArmySyncPacket remove(UUID armyId) {
        return new ArmySyncPacket(false, List.of(), List.of(armyId));
    }

    public static void encode(ArmySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.replaceAll);
        buffer.writeVarInt(packet.armies.size());
        packet.armies.forEach(army -> writeArmy(buffer, army));
        buffer.writeVarInt(packet.removedArmyIds.size());
        packet.removedArmyIds.forEach(buffer::writeUUID);
    }

    public static ArmySyncPacket decode(FriendlyByteBuf buffer) {
        boolean replaceAll = buffer.readBoolean();
        int count = checkedCount(buffer.readVarInt());
        List<CampaignArmySnapshot> armies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) armies.add(readArmy(buffer));
        int removedCount = checkedCount(buffer.readVarInt());
        List<UUID> removed = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++) removed.add(buffer.readUUID());
        return new ArmySyncPacket(replaceAll, armies, removed);
    }

    private static void writeArmy(FriendlyByteBuf buffer, CampaignArmySnapshot army) {
        buffer.writeUUID(army.armyId());
        buffer.writeUUID(army.ownerId());
        buffer.writeUtf(army.armyName(), 64);
        buffer.writeBoolean(army.commanderId() != null);
        if (army.commanderId() != null) buffer.writeUUID(army.commanderId());
        buffer.writeVarInt(army.groupIds().size());
        army.groupIds().forEach(buffer::writeUUID);
        buffer.writeResourceLocation(army.flagTexture());
        buffer.writeDouble(army.flagAspectRatio());
        writeVec(buffer, army.campaignPosition());
        buffer.writeVarInt(army.movementPath().size());
        army.movementPath().forEach(point -> writeVec(buffer, point));
        buffer.writeEnum(army.state());
        buffer.writeVarInt(army.foodSupplies());
        buffer.writeDouble(army.fatigue());
        buffer.writeVarInt(army.totalSoldiers());
        buffer.writeVarInt(army.transitionTicksRemaining());
        buffer.writeVarInt(army.transitionTicksTotal());
    }

    private static CampaignArmySnapshot readArmy(FriendlyByteBuf buffer) {
        UUID armyId = buffer.readUUID();
        UUID ownerId = buffer.readUUID();
        String name = buffer.readUtf(64);
        UUID commanderId = buffer.readBoolean() ? buffer.readUUID() : null;
        int groupCount = checkedCount(buffer.readVarInt());
        List<UUID> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) groups.add(buffer.readUUID());
        ResourceLocation texture = buffer.readResourceLocation();
        double aspect = buffer.readDouble();
        Vec3 position = readVec(buffer);
        int pathCount = checkedCount(buffer.readVarInt());
        List<Vec3> path = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) path.add(readVec(buffer));
        ArmyState state = buffer.readEnum(ArmyState.class);
        int food = buffer.readVarInt();
        double fatigue = buffer.readDouble();
        int soldiers = buffer.readVarInt();
        int transitionRemaining = buffer.readVarInt();
        int transitionTotal = buffer.readVarInt();
        return new CampaignArmySnapshot(armyId, ownerId, name, commanderId, groups, texture, aspect,
                position, path, state, food, fatigue, soldiers, transitionRemaining, transitionTotal);
    }

    private static void writeVec(FriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static int checkedCount(int count) {
        if (count < 0 || count > 10_000) throw new IllegalArgumentException("Invalid campaign army collection size " + count);
        return count;
    }
}
