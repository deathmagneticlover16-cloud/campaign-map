package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.CampaignMapMod;
import com.lastpagans.campaignmap.network.CampaignNetwork;
import immersive_paintings.client.gui.ImmersivePaintingScreen;
import immersive_paintings.entity.ImmersivePaintingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

/** The real Immersive Paintings 0.6.13 browser, redirected to an army flag selection. */
public final class CampaignFlagChooserScreen extends ImmersivePaintingScreen {
    private static final Field ENTITY_FIELD = resolveEntityField();
    private final UUID armyId;
    private ImmersivePaintingEntity proxy;
    private boolean ready;

    public CampaignFlagChooserScreen(UUID armyId) {
        super(Integer.MIN_VALUE, 8, 256, true, 0);
        this.armyId = armyId;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            proxy = createProxy(minecraft);
            ready = installProxy(proxy);
        }
    }

    public static void open(UUID armyId) {
        Minecraft.getInstance().setScreen(new CampaignFlagChooserScreen(armyId));
    }

    private static Field resolveEntityField() {
        try {
            Field field = ImmersivePaintingScreen.class.getDeclaredField("entity");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CampaignMapMod.LOGGER.error("Immersive Paintings 0.6.13 editor entity field is unavailable", error);
            return null;
        }
    }

    private boolean installProxy(ImmersivePaintingEntity proxy) {
        if (ENTITY_FIELD == null || proxy == null) return false;
        try {
            ENTITY_FIELD.set(this, proxy);
            return ENTITY_FIELD.get(this) == proxy;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CampaignMapMod.LOGGER.error("Could not attach the army-flag proxy to Immersive Paintings", error);
            return false;
        }
    }

    private static ImmersivePaintingEntity createProxy(Minecraft minecraft) {
        try {
            Class<?> entities = Class.forName("immersive_paintings.Entities");
            Object holder = entities.getField("PAINTING").get(null);
            Object entityType = holder instanceof java.util.function.Supplier<?> supplier
                    ? supplier.get() : holder.getClass().getMethod("get").invoke(holder);
            for (Constructor<?> constructor : ImmersivePaintingEntity.class.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 2 && parameters[0].isInstance(entityType)
                        && parameters[1].isInstance(minecraft.level)) {
                    return (ImmersivePaintingEntity) constructor.newInstance(entityType, minecraft.level);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            CampaignMapMod.LOGGER.error("Could not create the Immersive Paintings army-flag proxy", error);
        }
        return null;
    }

    @Override
    public void setPage(Page page) {
        if (ready && page == Page.FRAME && proxy != null) {
            try {
                Object motive = proxy.getClass().getMethod("getMotive").invoke(proxy);
                if (motive != null) {
                    ResourceLocation selected = new ResourceLocation(motive.toString());
                    var resolved = FlagTextureResolver.resolvePainting(selected,
                            CampaignBannerRenderer.DEFAULT_TEXTURE, 1.5D);
                    CampaignNetwork.requestArmyFlag(armyId, selected, resolved.aspect());
                    Minecraft.getInstance().setScreen(null);
                    return;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                CampaignMapMod.LOGGER.error("Immersive Paintings could not complete the army flag selection", error);
            }
        }
        super.setPage(page);
    }

    @Override
    public void onClose() {
        if (ready) super.onClose();
    }
}
