package com.lastpagans.campaignmap.client.army;

import com.lastpagans.campaignmap.api.army.ArmyState;
import com.lastpagans.campaignmap.api.army.CampaignArmySnapshot;
import com.lastpagans.campaignmap.network.ArmySyncPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientArmyCache {
    private static final Map<UUID, Entry> ARMIES = new LinkedHashMap<>();
    private static long revision;

    private ClientArmyCache() {}

    public static void apply(ArmySyncPacket packet) {
        if (packet.replaceAll()) ARMIES.clear();
        packet.removedArmyIds().forEach(ARMIES::remove);
        double now = monotonicSeconds();
        for (CampaignArmySnapshot army : packet.armies()) {
            Entry existing = ARMIES.get(army.armyId());
            if (existing == null) ARMIES.put(army.armyId(), new Entry(army, now));
            else existing.accept(army, now);
        }
        revision++;
    }

    public static void smooth(double deltaSeconds) {
        double now = monotonicSeconds();
        double dt = Math.max(0.0D, Math.min(0.1D, deltaSeconds));
        double angleAlpha = 1.0D - Math.exp(-dt * 10.0D);
        ARMIES.values().forEach(entry -> {
            entry.sample(now);
            entry.movementSpeed = entry.visualVelocity.horizontalDistance();
            if (entry.visualVelocity.horizontalDistanceSqr() > 1.0e-6D) {
                float travelYaw = (float) Math.toDegrees(Math.atan2(
                        -entry.visualVelocity.x, entry.visualVelocity.z));
                entry.bodyYaw += wrapDegrees(travelYaw - entry.bodyYaw) * (float) angleAlpha;
            }
        });
    }

    public static Collection<Entry> entries() { return Collections.unmodifiableCollection(ARMIES.values()); }
    public static Optional<Entry> get(UUID id) { return Optional.ofNullable(ARMIES.get(id)); }
    public static long revision() { return revision; }

    private static double monotonicSeconds() { return System.nanoTime() / 1_000_000_000.0D; }

    public static final class Entry {
        private static final double INTERPOLATION_DELAY = 0.38D;
        private static final double MAX_EXTRAPOLATION = 0.08D;
        private static final int MAX_SAMPLES = 12;

        private CampaignArmySnapshot snapshot;
        private final Deque<PositionSample> samples = new ArrayDeque<>();
        private Vec3 displayedPosition;
        private Vec3 visualVelocity = Vec3.ZERO;
        private float bodyYaw;
        private double movementSpeed;

        private Entry(CampaignArmySnapshot snapshot, double now) {
            this.snapshot = snapshot;
            this.displayedPosition = snapshot.campaignPosition();
            samples.addLast(new PositionSample(now, displayedPosition));
        }

        private void accept(CampaignArmySnapshot next, double now) {
            boolean movementChanged = advancing(snapshot) != advancing(next);
            boolean positionChanged = next.campaignPosition().distanceToSqr(snapshot.campaignPosition()) > 1.0e-10D;
            PositionSample previous = samples.peekLast();
            if (previous == null || now - previous.time > 1.0D) {
                snapshot = next;
                samples.clear();
                samples.addLast(new PositionSample(now, next.campaignPosition()));
                displayedPosition = next.campaignPosition();
                visualVelocity = Vec3.ZERO;
                return;
            }
            snapshot = next;
            if (!positionChanged && !movementChanged) return;
            double time = Math.max(now, previous.time + 0.001D);
            samples.addLast(new PositionSample(time, next.campaignPosition()));
            while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        }

        private void sample(double now) {
            if (samples.isEmpty()) return;
            List<PositionSample> history = new ArrayList<>(samples);
            double renderTime = now - INTERPOLATION_DELAY;
            if (history.size() == 1 || renderTime <= history.get(0).time) {
                displayedPosition = history.get(0).position;
                visualVelocity = Vec3.ZERO;
                return;
            }

            int segment = -1;
            for (int i = 0; i + 1 < history.size(); i++) {
                if (renderTime <= history.get(i + 1).time) {
                    segment = i;
                    break;
                }
            }
            if (segment < 0) {
                PositionSample last = history.get(history.size() - 1);
                Vec3 velocity = history.size() < 2 ? Vec3.ZERO
                        : tangent(history.get(history.size() - 2), last);
                if (!advancing(snapshot)) velocity = Vec3.ZERO;
                double ahead = Math.min(MAX_EXTRAPOLATION, Math.max(0.0D, renderTime - last.time));
                displayedPosition = last.position.add(velocity.scale(ahead));
                visualVelocity = velocity;
                return;
            }

            PositionSample a = history.get(segment);
            PositionSample b = history.get(segment + 1);
            PositionSample before = segment > 0 ? history.get(segment - 1) : a;
            PositionSample after = segment + 2 < history.size() ? history.get(segment + 2) : b;
            double duration = Math.max(0.001D, b.time - a.time);
            double t = Math.max(0.0D, Math.min(1.0D, (renderTime - a.time) / duration));
            Vec3 segmentDelta = b.position.subtract(a.position);
            double maximumSpeed = segmentDelta.length() / duration * 2.0D;
            Vec3 velocityA = constrainTangent(tangent(before, b), segmentDelta, maximumSpeed);
            Vec3 velocityB = constrainTangent(tangent(a, after), segmentDelta, maximumSpeed);
            displayedPosition = hermite(a.position, velocityA, b.position, velocityB, duration, t);
            visualVelocity = hermiteVelocity(a.position, velocityA, b.position, velocityB, duration, t);
        }

        public CampaignArmySnapshot snapshot() { return snapshot; }
        public Vec3 displayedPosition() { return displayedPosition; }
        public float bodyYaw() { return bodyYaw; }
        public double movementSpeed() { return movementSpeed; }

        private static boolean advancing(CampaignArmySnapshot army) {
            return army.state() == ArmyState.MOVING && !army.movementPath().isEmpty();
        }

        private static Vec3 tangent(PositionSample from, PositionSample to) {
            double duration = to.time - from.time;
            return duration <= 0.001D ? Vec3.ZERO : to.position.subtract(from.position).scale(1.0D / duration);
        }

        private static Vec3 constrainTangent(Vec3 tangent, Vec3 segmentDelta, double maximumSpeed) {
            if (segmentDelta.lengthSqr() < 1.0e-10D || tangent.dot(segmentDelta) <= 0.0D) return Vec3.ZERO;
            double speed = tangent.length();
            return speed <= maximumSpeed || speed < 1.0e-10D
                    ? tangent : tangent.scale(maximumSpeed / speed);
        }

        private static Vec3 hermite(Vec3 a, Vec3 velocityA, Vec3 b, Vec3 velocityB,
                                    double duration, double t) {
            double t2 = t * t, t3 = t2 * t;
            return a.scale(2.0D * t3 - 3.0D * t2 + 1.0D)
                    .add(velocityA.scale((t3 - 2.0D * t2 + t) * duration))
                    .add(b.scale(-2.0D * t3 + 3.0D * t2))
                    .add(velocityB.scale((t3 - t2) * duration));
        }

        private static Vec3 hermiteVelocity(Vec3 a, Vec3 velocityA, Vec3 b, Vec3 velocityB,
                                            double duration, double t) {
            double t2 = t * t;
            return a.scale(6.0D * t2 - 6.0D * t)
                    .add(velocityA.scale((3.0D * t2 - 4.0D * t + 1.0D) * duration))
                    .add(b.scale(-6.0D * t2 + 6.0D * t))
                    .add(velocityB.scale((3.0D * t2 - 2.0D * t) * duration))
                    .scale(1.0D / duration);
        }

        private record PositionSample(double time, Vec3 position) {}
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) degrees -= 360.0F;
        if (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }
}
