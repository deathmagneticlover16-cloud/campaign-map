package com.lastpagans.campaignmap.client.army;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

final class FlagTextureResolver {
    private FlagTextureResolver() {}
    static Flag resolvePainting(ResourceLocation id, ResourceLocation fallback, double fallbackAspect) {
        if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) return new Flag(id, fallbackAspect);
        try {
            Class<?> manager = Class.forName("immersive_paintings.resources.ClientPaintingManager");
            Class<?> type = Class.forName("immersive_paintings.resources.Painting$Type");
            Object full = Enum.valueOf((Class) type, "FULL");
            Method method = Arrays.stream(manager.getMethods()).filter(m -> m.getName().equals("getPaintingTexture")).findFirst().orElseThrow();
            Object painting = manager.getMethod("getPainting", ResourceLocation.class).invoke(null, id);
            Object value = method.invoke(null, id, full);
            ResourceLocation texture = (ResourceLocation) value.getClass().getField("textureIdentifier").get(value);
            int width = painting == null ? 0 : painting.getClass().getField("width").getInt(painting);
            int height = painting == null ? 0 : painting.getClass().getField("height").getInt(painting);
            double aspect = height <= 0 ? fallbackAspect : Math.max(0.5, Math.min(4.0, width / (double) height));
            return new Flag(texture, aspect);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return new Flag(fallback, fallbackAspect); }
    }
    static Flag resolveGroup(UUID groupId, ResourceLocation fallback) {
        try {
            Object value = Class.forName("ee.lastpagans.recruitsrtscamera.client.GroupFlagClientState")
                    .getMethod("texture", UUID.class).invoke(null, groupId);
            if (value != null) return new Flag((ResourceLocation) value.getClass().getMethod("location").invoke(value),
                    ((Number) value.getClass().getMethod("aspectRatio").invoke(value)).doubleValue());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        return new Flag(fallback, 1.5);
    }
    record Flag(ResourceLocation texture, double aspect) {}
}
