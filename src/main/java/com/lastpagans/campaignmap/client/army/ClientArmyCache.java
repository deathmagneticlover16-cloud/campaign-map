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
            else existing.accept(army);
        }
        revision++;
    }

    public static void smooth(double deltaSeconds) {
        double dt = Math.max(0.0, Math.min(0.1, deltaSeconds));
        double angleAlpha = 1.0 - Math.exp(-dt * 12.0);
        ARMIES.values().forEach(entry -> {
            entry.advance(dt);
            entry.movementSpeed = entry.visualVelocity.horizontalDistance();
            if (entry.visualVelocity.horizontalDistanceSqr() > 1.0e-6) {
                float targetYaw = (float) Math.toDegrees(Math.atan2(
                        -entry.visualVelocity.x, entry.visualVelocity.z));
                entry.bodyYaw += wrapDegrees(targetYaw - entry.bodyYaw) * (float) angleAlpha;
            }
        });
    }

    public static Collection<Entry> entries() { return Collections.unmodifiableCollection(ARMIES.values()); }
    public static Optional<Entry> get(UUID id) { return Optional.ofNullable(ARMIES.get(id)); }
    public static long revision() { return revision; }

    public static final class Entry {
        private CampaignArmySnapshot snapshot;
        private Vec3 displayedPosition;
        private Vec3 segmentStart;
        private Vec3 networkTarget;
        private Vec3 startVelocity = Vec3.ZERO;
        private Vec3 targetVelocity = Vec3.ZERO;
        private Vec3 visualVelocity = Vec3.ZERO;
        private double segmentElapsed;
        private double segmentDuration = 0.25D;
        private long lastSnapshotNanos;
        private float bodyYaw;
        private double movementSpeed;

        private Entry(CampaignArmySnapshot snapshot, Vec3 displayedPosition) {
            this.snapshot = snapshot;
            this.displayedPosition = displayedPosition;
            this.segmentStart = displayedPosition;
            this.networkTarget = displayedPosition;
            this.lastSnapshotNanos = System.nanoTime();
        }

        private void accept(CampaignArmySnapshot next) {
            long now = System.nanoTime();
            double interval = Math.max(0.10D, Math.min(0.50D,
                    (now - lastSnapshotNanos) / 1_000_000_000.0D));
            lastSnapshotNanos = now;
            boolean wasAdvancing = advancing(snapshot);
            boolean willAdvance = advancing(next);
            Vec3 nextTarget = next.campaignPosition();
            if (nextTarget.distanceToSqr(networkTarget) < 1.0e-10D && wasAdvancing == willAdvance) {
                snapshot = next;
                return;
            }
            Vec3 estimatedVelocity = willAdvance
                    ? limitVelocity(nextTarget.subtract(networkTarget).scale(1.0D / interval),
                    displayedPosition.distanceTo(nextTarget) / interval * 1.5D)
                    : Vec3.ZERO;
            snapshot = next;
            segmentStart = displayedPosition;
            networkTarget = nextTarget;
            startVelocity = visualVelocity;
            targetVelocity = estimatedVelocity;
            segmentElapsed = 0.0D;
            segmentDuration = interval;
        }

        private void advance(double deltaSeconds) {
            if (segmentDuration <= 0.0D) return;
            segmentElapsed = Math.min(segmentDuration, segmentElapsed + deltaSeconds);
            double t = segmentElapsed / segmentDuration;
            double t2 = t * t;
            double t3 = t2 * t;
            double h00 = 2.0D * t3 - 3.0D * t2 + 1.0D;
            double h10 = t3 - 2.0D * t2 + t;
            double h01 = -2.0D * t3 + 3.0D * t2;
            double h11 = t3 - t2;
            displayedPosition = segmentStart.scale(h00)
                    .add(startVelocity.scale(h10 * segmentDuration))
                    .add(networkTarget.scale(h01))
                    .add(targetVelocity.scale(h11 * segmentDuration));

            double dh00 = 6.0D * t2 - 6.0D * t;
            double dh10 = 3.0D * t2 - 4.0D * t + 1.0D;
            double dh01 = -dh00;
            double dh11 = 3.0D * t2 - 2.0D * t;
            visualVelocity = segmentStart.scale(dh00)
                    .add(startVelocity.scale(dh10 * segmentDuration))
                    .add(networkTarget.scale(dh01))
                    .add(targetVelocity.scale(dh11 * segmentDuration))
                    .scale(1.0D / segmentDuration);
            if (segmentElapsed >= segmentDuration) {
                displayedPosition = networkTarget;
                visualVelocity = targetVelocity;
            }
        }

        private static boolean advancing(CampaignArmySnapshot army) {
            return army.state() == com.lastpagans.campaignmap.api.army.ArmyState.MOVING
                    && !army.movementPath().isEmpty();
        }

        private static Vec3 limitVelocity(Vec3 velocity, double maximum) {
            if (!Double.isFinite(maximum) || maximum <= 0.0D) return Vec3.ZERO;
            double length = velocity.length();
            return length <= maximum ? velocity : velocity.scale(maximum / length);
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
