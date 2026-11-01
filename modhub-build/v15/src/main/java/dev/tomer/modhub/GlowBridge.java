package dev.tomer.modhub;

import java.util.List;

/** Shared, Minecraft-mapping-independent data between scan and world renderer. */
public final class GlowBridge {
    private GlowBridge() { }
    public record GlowPoint(int x, int y, int z, int type) { }
    public static volatile boolean enabled;
    public static volatile int range = 64;
    public static volatile int savedCount;
    public static volatile List<GlowPoint> points = List.of();
}

