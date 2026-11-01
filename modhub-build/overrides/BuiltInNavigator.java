package dev.tomer.modhub;

import net.minecraft.class_310;
import net.minecraft.class_746;
import net.minecraft.class_2338;

/**
 * Lightweight, self-contained walking assistant. It is NOT a full Baritone pathfinder:
 * it steers, jumps over one-block obstacles and stops at cliffs, liquids, or when stuck.
 * No dependencies beyond Fabric API. Never sends movement directly to the server.
 */
public final class BuiltInNavigator {
    private boolean active;
    private int goalX, goalZ, stuckTicks;
    private double previousX, previousZ;
    private String status = "Ready";
    public boolean isActive() { return active; }
    public String status() { return status; }
    public void start(class_310 mc, int x, int z) {
        stop(mc);
        goalX = x; goalZ = z;
        previousX = mc.field_1724.method_23317();
        previousZ = mc.field_1724.method_23321();
        stuckTicks = 0;
        active = true;
        status = "Walking to X=" + goalX + " Z=" + goalZ;
    }
    private void keys(class_310 mc, boolean forward, boolean jump) {
        if (mc == null || mc.field_1690 == null) return;
        mc.field_1690.field_1894.method_23481(forward);
        mc.field_1690.field_1903.method_23481(jump);
    }
    public void stop(class_310 mc) {
        keys(mc, false, false);
        if (active) status = "Stopped";
        active = false;
    }
    public void tick(class_310 mc) {
        if (!active) return;
        if (mc == null || mc.field_1724 == null || mc.field_1687 == null) { stop(mc); status = "Left world"; return; }
        if (mc.field_1755 != null) { keys(mc, false, false); return; }
        class_746 p = mc.field_1724;
        double dx = goalX + 0.5 - p.method_23317(), dz = goalZ + 0.5 - p.method_23321();
        double dist = Math.hypot(dx, dz);
        if (dist < 2.25) { stop(mc); status = "Destination reached"; return; }
        // Minecraft yaw 0 = south (+Z), -90 = east (+X).
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.method_36456(yaw);
        double aheadX = p.method_23317() + dx / dist * 1.35;
        double aheadZ = p.method_23321() + dz / dist * 1.35;
        int fx = (int)Math.floor(aheadX), fz = (int)Math.floor(aheadZ);
        int foot = (int)Math.floor(p.method_23318() + 0.05);
        class_2338 ahead = new class_2338(fx, foot, fz);
        String atFeet = mc.field_1687.method_8320(ahead).method_26204().toString().toLowerCase();
        String below = mc.field_1687.method_8320(new class_2338(fx, foot-1, fz)).method_26204().toString().toLowerCase();
        String lower = mc.field_1687.method_8320(new class_2338(fx, foot-2, fz)).method_26204().toString().toLowerCase();
        if (danger(atFeet) || danger(below) || danger(lower)) {
            stop(mc); status = "Stopped: water/lava hazard"; return;
        }
        boolean floorMissing = empty(below) && empty(lower);
        if (floorMissing) { stop(mc); status = "Stopped: cliff/drop ahead"; return; }
        String head = mc.field_1687.method_8320(new class_2338(fx, foot+1, fz)).method_26204().toString().toLowerCase();
        boolean jump = !empty(atFeet) && empty(head) && p.method_24828();
        if (!empty(atFeet) && !empty(head)) { stop(mc); status = "Stopped: blocked by wall"; return; }
        if (Math.hypot(p.method_23317()-previousX, p.method_23321()-previousZ) < 0.03) stuckTicks++;
        else stuckTicks = 0;
        previousX = p.method_23317(); previousZ = p.method_23321();
        if (stuckTicks > 90) { stop(mc); status = "Stopped: path blocked"; return; }
        keys(mc, true, jump);
        status = "Walking - " + Math.round(dist) + " blocks left";
    }
    private boolean empty(String s) {
        return s.contains("minecraft:air") || s.contains("cave_air") || s.contains("void_air") || s.contains("grass") || s.contains("flower") || s.contains("torch") || s.contains("fern") || s.contains("snow");
    }
    private boolean danger(String s) { return s.contains("lava") || s.contains("water") || s.contains("magma") || s.contains("fire"); }
}