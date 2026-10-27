package com.lastpagans.campaignmap.client;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Smooth perspective camera for the self-contained campaign scene. Coordinates are map-image pixels. */
public final class CampaignCamera3D {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private double focusX;
    private double focusZ;
    private double distance;
    private double pitch = Math.toRadians(58.0);
    private double yaw;
    private double roll;
    private double targetFocusX;
    private double targetFocusZ;
    private double targetDistance;
    private double targetPitch = pitch;
    private double targetYaw;
    private double targetRoll;
    private int viewportX;
    private int viewportY;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int mapWidth = 1;
    private int mapHeight = 1;
    private Vec3 position = Vec3.ZERO;
    private Vec3 forward = new Vec3(0.0, -1.0, 0.0);
    private Vec3 right = new Vec3(1.0, 0.0, 0.0);
    private Vec3 up = new Vec3(0.0, 0.0, -1.0);

    public void initialize(int mapWidth, int mapHeight) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        focusX = targetFocusX = mapWidth * 0.5;
        focusZ = targetFocusZ = mapHeight * 0.5;
        distance = targetDistance = Math.max(mapWidth, mapHeight) * 1.18;
        rebuildBasis();
    }

    public void viewport(int x, int y, int width, int height) {
        viewportX = x;
        viewportY = y;
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);
    }

    public void update(double deltaSeconds) {
        double dt = Math.max(0.0, Math.min(0.1, deltaSeconds));
        double positionAlpha = 1.0 - Math.exp(-dt * 10.0);
        double angleAlpha = 1.0 - Math.exp(-dt * 8.0);
        focusX = lerp(focusX, targetFocusX, positionAlpha);
        focusZ = lerp(focusZ, targetFocusZ, positionAlpha);
        distance = lerp(distance, targetDistance, positionAlpha);
        pitch = lerp(pitch, targetPitch, angleAlpha);
        yaw = lerpAngle(yaw, targetYaw, angleAlpha);
        roll = lerp(roll, targetRoll, angleAlpha);
        targetRoll *= Math.exp(-dt * 7.0);
        rebuildBasis();
    }

    public void zoom(double wheelAmount) {
        if (wheelAmount == 0.0) return;
        double factor = Math.pow(1.16, -wheelAmount);
        targetDistance = clamp(targetDistance * factor, Math.max(90.0, Math.min(mapWidth, mapHeight) * 0.30),
                Math.max(mapWidth, mapHeight) * 3.2);
        targetPitch = clamp(Math.toRadians(52.0) + (targetDistance / (Math.max(mapWidth, mapHeight) * 3.2))
                * Math.toRadians(13.0), Math.toRadians(48.0), Math.toRadians(68.0));
    }

    public void pan(double previousMouseX, double previousMouseY, double mouseX, double mouseY) {
        MapPoint before = screenToMap(previousMouseX, previousMouseY);
        MapPoint after = screenToMap(mouseX, mouseY);
        if (before != null && after != null) {
            targetFocusX = clamp(targetFocusX + before.x - after.x, 0.0, mapWidth);
            targetFocusZ = clamp(targetFocusZ + before.y - after.y, 0.0, mapHeight);
        }
        targetRoll = clamp(-(mouseX - previousMouseX) * 0.003, -0.05, 0.05);
    }

    public void orbit(double deltaX, double deltaY) {
        targetYaw += deltaX * 0.006;
        targetPitch = clamp(targetPitch + deltaY * 0.004, Math.toRadians(38.0), Math.toRadians(78.0));
        targetRoll = clamp(-deltaX * 0.002, -0.04, 0.04);
    }

    public void finishGesture() { targetRoll = 0.0; }

    public ProjectedPoint project(double mapX, double elevation, double mapY) {
        Vec3 delta = new Vec3(mapX, elevation, mapY).subtract(position);
        double cameraX = delta.dot(right);
        double cameraY = delta.dot(up);
        double depth = delta.dot(forward);
        if (depth <= 0.5) return new ProjectedPoint(0.0, 0.0, depth, false);
        double focal = focalLength();
        double screenX = viewportX + viewportWidth * 0.5 + cameraX * focal / depth;
        double screenY = viewportY + viewportHeight * 0.5 - cameraY * focal / depth;
        boolean visible = screenX >= viewportX - 64 && screenX <= viewportX + viewportWidth + 64
                && screenY >= viewportY - 64 && screenY <= viewportY + viewportHeight + 64;
        return new ProjectedPoint(screenX, screenY, depth, visible);
    }

    @Nullable
    public MapPoint screenToMap(double screenX, double screenY) {
        double cameraX = (screenX - (viewportX + viewportWidth * 0.5)) / focalLength();
        double cameraY = -(screenY - (viewportY + viewportHeight * 0.5)) / focalLength();
        Vec3 ray = forward.add(right.scale(cameraX)).add(up.scale(cameraY)).normalize();
        if (Math.abs(ray.y) < 1.0e-6) return null;
        double time = -position.y / ray.y;
        if (time <= 0.0) return null;
        Vec3 hit = position.add(ray.scale(time));
        return new MapPoint(hit.x, hit.z);
    }

    public boolean insideViewport(double x, double y) {
        return x >= viewportX && x < viewportX + viewportWidth && y >= viewportY && y < viewportY + viewportHeight;
    }

    public Vec3 right() { return right; }
    public Vec3 forward() { return forward; }
    public float yawDegrees() { return (float) Math.toDegrees(yaw); }
    public float pitchDegrees() { return (float) Math.toDegrees(pitch); }
    public double pixelsPerMapUnit(double depth) { return depth <= 0.5 ? 0.0 : focalLength() / depth; }

    private void rebuildBasis() {
        double cosPitch = Math.cos(pitch);
        forward = new Vec3(Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch).normalize();
        position = new Vec3(focusX, 0.0, focusZ).subtract(forward.scale(distance));
        Vec3 unrolledRight = WORLD_UP.cross(forward).normalize();
        Vec3 unrolledUp = forward.cross(unrolledRight).normalize();
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        right = unrolledRight.scale(cosRoll).add(unrolledUp.scale(sinRoll));
        up = unrolledUp.scale(cosRoll).subtract(unrolledRight.scale(sinRoll));
    }

    private double focalLength() {
        return viewportHeight / (2.0 * Math.tan(Math.toRadians(52.0) * 0.5));
    }

    private static double lerp(double from, double to, double alpha) { return from + (to - from) * alpha; }
    private static double lerpAngle(double from, double to, double alpha) {
        double difference = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + difference * alpha;
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record ProjectedPoint(double x, double y, double depth, boolean visible) {}
    public record MapPoint(double x, double y) {}
}
