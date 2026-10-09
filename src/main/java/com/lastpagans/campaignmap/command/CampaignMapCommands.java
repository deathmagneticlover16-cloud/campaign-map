package com.lastpagans.campaignmap.command;

import com.lastpagans.campaignmap.api.ProvinceDefinition;
import com.lastpagans.campaignmap.api.ProvinceOwnerState;
import com.lastpagans.campaignmap.data.CampaignOwnershipData;
import com.lastpagans.campaignmap.geography.MapProjection;
import com.lastpagans.campaignmap.geography.ProvinceRegistry;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class CampaignMapCommands {
    private static final SimpleCommandExceptionType UNKNOWN_PROVINCE =
            new SimpleCommandExceptionType(Component.literal("Unknown campaign province."));

    private CampaignMapCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("campaignmap")
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("where")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(context -> where(context.getSource(),
                                                DoubleArgumentType.getDouble(context, "x"),
                                                DoubleArgumentType.getDouble(context, "z"))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("province", ResourceLocationArgument.id())
                                .executes(context -> info(context.getSource(), ResourceLocationArgument.getId(context, "province")))))
                .then(Commands.literal("setowner").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("province", ResourceLocationArgument.id())
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .then(Commands.argument("hexColor", StringArgumentType.word())
                                                .executes(context -> setIdentity(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "province"),
                                                        StringArgumentType.getString(context, "owner"),
                                                        StringArgumentType.getString(context, "hexColor"), true))))))
                .then(Commands.literal("clearowner").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("province", ResourceLocationArgument.id())
                                .executes(context -> clearIdentity(context.getSource(),
                                        ResourceLocationArgument.getId(context, "province"), true))))
                .then(Commands.literal("setcontroller").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("province", ResourceLocationArgument.id())
                                .then(Commands.argument("controller", StringArgumentType.word())
                                        .then(Commands.argument("hexColor", StringArgumentType.word())
                                                .executes(context -> setIdentity(context.getSource(),
                                                        ResourceLocationArgument.getId(context, "province"),
                                                        StringArgumentType.getString(context, "controller"),
                                                        StringArgumentType.getString(context, "hexColor"), false))))))
                .then(Commands.literal("clearcontroller").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("province", ResourceLocationArgument.id())
                                .executes(context -> clearIdentity(context.getSource(),
                                        ResourceLocationArgument.getId(context, "province"), false)))));
    }

    private static int list(CommandSourceStack source) {
        ProvinceRegistry registry = ProvinceRegistry.get();
        source.sendSuccess(() -> Component.literal("Campaign provinces (" + registry.provinces().size() + "):"), false);
        for (ProvinceDefinition province : registry.provinces()) {
            source.sendSuccess(() -> Component.literal("- " + province.id() + " — " + province.name()), false);
        }
        return registry.provinces().size();
    }

    private static int where(CommandSourceStack source, double x, double z) {
        ProvinceRegistry registry = ProvinceRegistry.get();
        MapProjection.Sample sample = registry.projection().worldToSample(x, z).orElse(null);
        if (sample == null) {
            source.sendFailure(Component.literal("World coordinate is outside the configured campaign map bounds."));
            return 0;
        }
        int rgb = registry.raster().rgbAt(sample.pixel().x(), sample.pixel().y());
        ProvinceDefinition province = registry.atPixel(sample.pixel().x(), sample.pixel().y());
        String resolved = province == null ? "NO PROVINCE" : province.id() + " / " + province.name();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World X/Z: %.3f, %.3f | normalized U/V: %.6f, %.6f | pixel: %d, %d | RGB: %d,%d,%d | province: %s",
                x, z, sample.u(), sample.v(), sample.pixel().x(), sample.pixel().y(),
                (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, resolved)), false);
        return province == null ? 0 : 1;
    }

    private static int info(CommandSourceStack source, ResourceLocation provinceId) throws CommandSyntaxException {
        ProvinceDefinition province = requireProvince(source, provinceId);
        ProvinceOwnerState state = CampaignOwnershipData.get(source.getLevel()).state(provinceId);
        source.sendSuccess(() -> Component.literal(province.name() + " (" + province.id() + ") | owner: "
                + identityText(state.owner()) + " | controller: " + identityText(state.controller())
                + " | neighbours: " + ProvinceRegistry.get().neighbours(provinceId)), false);
        return 1;
    }

    private static int setIdentity(CommandSourceStack source, ResourceLocation provinceId, String name,
                                   String colorText, boolean owner) throws CommandSyntaxException {
        requireProvince(source, provinceId);
        int color;
        try {
            String normalized = colorText.startsWith("#") ? colorText.substring(1) : colorText;
            if (!normalized.matches("[0-9a-fA-F]{6}")) throw new NumberFormatException();
            color = Integer.parseInt(normalized, 16);
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.literal("Colour must contain exactly six hexadecimal digits, for example 52753D."));
            return 0;
        }
        CampaignOwnershipData data = CampaignOwnershipData.get(source.getLevel());
        ProvinceOwnerState.PoliticalIdentity identity = new ProvinceOwnerState.PoliticalIdentity(name, color);
        ProvinceOwnerState state = owner ? data.setOwner(provinceId, identity) : data.setController(provinceId, identity);
        CampaignNetwork.sendUpdate(state);
        source.sendSuccess(() -> Component.literal((owner ? "Owner" : "Controller") + " for " + provinceId
                + " set to " + name + " (#" + String.format("%06X", color) + ")."), true);
        return 1;
    }

    private static int clearIdentity(CommandSourceStack source, ResourceLocation provinceId, boolean owner)
            throws CommandSyntaxException {
        requireProvince(source, provinceId);
        CampaignOwnershipData data = CampaignOwnershipData.get(source.getLevel());
        ProvinceOwnerState state = owner ? data.setOwner(provinceId, null) : data.setController(provinceId, null);
        CampaignNetwork.sendUpdate(state);
        source.sendSuccess(() -> Component.literal((owner ? "Owner" : "Controller") + " cleared for " + provinceId + "."), true);
        return 1;
    }

    private static ProvinceDefinition requireProvince(CommandSourceStack source, ResourceLocation id)
            throws CommandSyntaxException {
        ProvinceDefinition province = ProvinceRegistry.get().byId(id);
        if (province == null) throw UNKNOWN_PROVINCE.create();
        return province;
    }

    private static String identityText(ProvinceOwnerState.PoliticalIdentity identity) {
        return identity == null ? "Unassigned" : identity.id() + " " + identity.colorHex();
    }
}
