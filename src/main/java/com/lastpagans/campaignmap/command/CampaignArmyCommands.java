package com.lastpagans.campaignmap.command;

import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.army.CampaignArmyData;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class CampaignArmyCommands {
    private static final DynamicCommandExceptionType INVALID_UUID =
            new DynamicCommandExceptionType(value -> Component.literal("Invalid UUID: " + value));
    private static final DynamicCommandExceptionType ARMY_ERROR =
            new DynamicCommandExceptionType(value -> Component.literal(value.toString()));

    private CampaignArmyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("campaignmap")
                .then(Commands.literal("army")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .executes(context -> info(context.getSource(), uuid(context.getArgument("army", String.class))))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> rename(context.getSource(),
                                                        uuid(context.getArgument("army", String.class)),
                                                        StringArgumentType.getString(context, "name"))))))
                        .then(Commands.literal("addgroup")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .executes(context -> addGroup(context.getSource(),
                                                        uuid(context.getArgument("army", String.class)),
                                                        uuid(context.getArgument("group", String.class)))))))
                        .then(Commands.literal("removegroup")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .executes(context -> removeGroup(context.getSource(),
                                                        uuid(context.getArgument("army", String.class)),
                                                        uuid(context.getArgument("group", String.class)))))))
                        .then(Commands.literal("move")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> move(context.getSource(),
                                                                uuid(context.getArgument("army", String.class)),
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "z")))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("army", StringArgumentType.word())
                                        .executes(context -> delete(context.getSource(),
                                                uuid(context.getArgument("army", String.class))))))));
    }

    private static int list(CommandSourceStack source) {
        var data = CampaignArmyData.get(source.getLevel());
        var armies = data.snapshots(source.getLevel());
        source.sendSuccess(() -> Component.literal("Campaign armies (" + armies.size() + "):"), false);
        armies.forEach(army -> source.sendSuccess(() -> Component.literal("- " + army.armyName() + " ["
                + army.armyId() + "] " + army.state() + " groups=" + army.groupIds().size()
                + " soldiers=" + army.totalSoldiers()), false));
        return armies.size();
    }

    private static int create(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CampaignArmySnapshot army = CampaignArmyData.get(source.getLevel()).create(source.getLevel(), player.getUUID(),
                name, player.getUUID(), new Vec3(player.getX(), 0.0, player.getZ()));
        CampaignNetwork.sendArmyUpdate(army);
        source.sendSuccess(() -> Component.literal("Created " + army.armyName() + " [" + army.armyId() + "]."), true);
        return 1;
    }

    private static int info(CommandSourceStack source, UUID armyId) throws CommandSyntaxException {
        CampaignArmySnapshot army = requireArmy(source, armyId);
        source.sendSuccess(() -> Component.literal(army.armyName() + " [" + army.armyId() + "] owner="
                + army.ownerId() + " commander=" + army.commanderId() + " state=" + army.state()
                + " position=" + formatPosition(army.campaignPosition()) + " groups=" + army.groupIds()
                + " soldiers=" + army.totalSoldiers() + " food=" + army.foodSupplies()
                + " fatigue=" + String.format("%.2f", army.fatigue())), false);
        return 1;
    }

    private static int rename(CommandSourceStack source, UUID armyId, String name) throws CommandSyntaxException {
        requireManage(source, armyId);
        CampaignArmyData data = CampaignArmyData.get(source.getLevel());
        data.rename(source.getLevel(), armyId, name);
        CampaignArmySnapshot army = data.snapshot(source.getLevel(), armyId).orElseThrow();
        CampaignNetwork.sendArmyUpdate(army);
        source.sendSuccess(() -> Component.literal("Renamed army to " + army.armyName() + "."), true);
        return 1;
    }

    private static int addGroup(CommandSourceStack source, UUID armyId, UUID groupId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        requireManage(source, armyId);
        CampaignArmyData data = CampaignArmyData.get(source.getLevel());
        var result = data.addGroup(source.getLevel(), armyId, player.getUUID(), groupId, source.hasPermission(2));
        if (result != CampaignArmyData.MembershipResult.SUCCESS) throw ARMY_ERROR.create("Cannot add group: " + result);
        CampaignNetwork.sendArmyUpdate(data.snapshot(source.getLevel(), armyId).orElseThrow());
        source.sendSuccess(() -> Component.literal("Added group " + groupId + " to army " + armyId + "."), true);
        return 1;
    }

    private static int removeGroup(CommandSourceStack source, UUID armyId, UUID groupId) throws CommandSyntaxException {
        requireManage(source, armyId);
        CampaignArmyData data = CampaignArmyData.get(source.getLevel());
        if (!data.removeGroup(armyId, groupId)) throw ARMY_ERROR.create("Army does not contain that group.");
        CampaignNetwork.sendArmyUpdate(data.snapshot(source.getLevel(), armyId).orElseThrow());
        source.sendSuccess(() -> Component.literal("Removed group " + groupId + " from army " + armyId + "."), true);
        return 1;
    }

    private static int move(CommandSourceStack source, UUID armyId, double x, double z) throws CommandSyntaxException {
        requireManage(source, armyId);
        if (!com.lastpagans.campaignmap.geography.ProvinceRegistry.get().projection().isWorldInsideMap(x, z)) {
            throw ARMY_ERROR.create("Destination is outside the configured campaign map.");
        }
        CampaignArmyData data = CampaignArmyData.get(source.getLevel());
        if (!data.setMovementOrder(armyId, new Vec3(x, 0.0, z))) throw ARMY_ERROR.create("Army cannot move in its current state.");
        CampaignNetwork.sendArmyUpdate(data.snapshot(source.getLevel(), armyId).orElseThrow());
        source.sendSuccess(() -> Component.literal("Movement order issued to " + armyId + "."), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, UUID armyId) throws CommandSyntaxException {
        requireManage(source, armyId);
        if (!CampaignArmyData.get(source.getLevel()).delete(armyId)) throw ARMY_ERROR.create("Army not found.");
        CampaignNetwork.sendArmyRemoval(armyId);
        source.sendSuccess(() -> Component.literal("Deleted army " + armyId + "; its groups remain in their owning mod."), true);
        return 1;
    }

    private static CampaignArmySnapshot requireArmy(CommandSourceStack source, UUID armyId) throws CommandSyntaxException {
        return CampaignArmyData.get(source.getLevel()).snapshot(source.getLevel(), armyId)
                .orElseThrow(() -> ARMY_ERROR.create("Unknown campaign army: " + armyId));
    }

    private static void requireManage(CommandSourceStack source, UUID armyId) throws CommandSyntaxException {
        CampaignArmySnapshot army = requireArmy(source, armyId);
        if (!source.hasPermission(2) && (source.getEntity() == null || !army.ownerId().equals(source.getEntity().getUUID()))) {
            throw ARMY_ERROR.create("You do not own that army.");
        }
    }

    private static UUID uuid(String value) throws CommandSyntaxException {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw INVALID_UUID.create(value); }
    }

    private static String formatPosition(Vec3 position) {
        return String.format("%.1f, %.1f", position.x, position.z);
    }
}
