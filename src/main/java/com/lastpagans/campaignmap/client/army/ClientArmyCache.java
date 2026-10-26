package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.network.ArmySyncPacket;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ClientArmyCache {
    private static final Map<UUID, Entry> ARMIES = new LinkedHashMap<>();
    private static long revision;

    private ClientArmyCache() {}

    public static void apply(ArmySyncPacket packet) {
        if (packet.replaceAll()) ARMIES.clear();
        packet.removedArmyIds().forEach(ARMIES::remove);
        for (CampaignArmySnapshot army : packet.armies()) {
            Entry existing = ARMIES.get(army.armyId());
            if (existing == null) ARMIES.put(army.armyId(), new Entry(army, army.campaignPosition()));
            else existing.snapshot = army;
        }
        revision++;
    }

    public static void smooth(double deltaSeconds) {
        double alpha = 1.0 - Math.exp(-Math.max(0.0, Math.min(0.25, deltaSeconds)) * 7.5);
        ARMIES.values().forEach(entry -> {
            Vec3 previous = entry.displayedPosition;
            entry.displayedPosition = previous.lerp(entry.snapshot.campaignPosition(), alpha);
            Vec3 delta = entry.displayedPosition.subtract(previous);
            double dt = Math.max(1.0e-4, deltaSeconds);
            double measuredSpeed = delta.horizontalDistance() / dt;
            entry.movementSpeed += (measuredSpeed - entry.movementSpeed) * alpha;
            if (delta.horizontalDistanceSqr() > 1.0e-7) {
                float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                entry.bodyYaw += wrapDegrees(targetYaw - entry.bodyYaw) * (float) alpha;
            }
        });
    }

    public static Collection<Entry> entries() { return Collections.unmodifiableCollection(ARMIES.values()); }
    public static Optional<Entry> get(UUID id) { return Optional.ofNullable(ARMIES.get(id)); }
    public static long revision() { return revision; }

    public static final class Entry {
        private CampaignArmySnapshot snapshot;
        private Vec3 displayedPosition;
        private float bodyYaw;
        private double movementSpeed;

        private Entry(CampaignArmySnapshot snapshot, Vec3 displayedPosition) {
            this.snapshot = snapshot;
            this.displayedPosition = displayedPosition;
        }

        public CampaignArmySnapshot snapshot() { return snapshot; }
        public Vec3 displayedPosition() { return displayedPosition; }
        public float bodyYaw() { return bodyYaw; }
        public double movementSpeed() { return movementSpeed; }
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) degrees -= 360.0F;
        if (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }
}
