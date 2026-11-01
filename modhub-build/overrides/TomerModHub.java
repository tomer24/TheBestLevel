package dev.tomer.modhub;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import net.minecraft.class_746;
import net.minecraft.class_2561;
import net.minecraft.class_634;
import net.minecraft.class_2960;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import org.lwjgl.glfw.GLFW;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.util.zip.ZipFile;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.lang.reflect.Method;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * All-in-one Fabric client module dashboard.
 * Only the two built-in modules execute automation. The Mods tab displays
 * installed Fabric mods and opens the mods directory; it cannot hot-load JARs.
 */
public final class TomerModHub implements ClientModInitializer {
    private static final int OPEN_KEY = 344; // GLFW_KEY_RIGHT_SHIFT
    private static final int TELEPORT_THRESHOLD = 32;
    private static final int MAX_MISSED_TELEPORTS = 3;
    private static final int TELEPORT_TIMEOUT = 240; // 12 seconds
    private static final int SETTLE_TICKS = 18;
    private static final int NEXT_RTP_GAP = 25;
    private static final int WORLD_MAX = 29_999_984;
    private static final String[] REGIONS = {"NA East", "NA West", "EU Central", "EU West", "Asia", "Oceania"};
    private static final String[] REGION_ARGUMENTS = {"east", "west", "eu central", "eu west", "asia", "oceania"};

    private class_310 client;
    private boolean priorKey;
    private boolean rtpRunning;
    private boolean waiting;
    private boolean teleported;
    private int waitTicks;
    private int stableTicks;
    private int gapTicks;
    private int attempts;
    private int missed;
    private int limit = 50_000;
    private int region = 0;
    private double originX, originZ, lastX, lastZ;
    private String rtpStatus = "Ready";
    private String navStatus = "Ready - built-in walking";
    private final BuiltInNavigator navigator = new BuiltInNavigator();
    private boolean chestEspEnabled = false;
    private boolean enderChestsEnabled = true;
    private boolean shulkersEnabled = true;
    private boolean torchesEnabled = false;
    private int chestEspRange = 64;
    private final ChestEspOverlay chestEsp = new ChestEspOverlay(this);
    private volatile String importStatus = "Select a Fabric mod .jar to import";
    private int navX, navZ;

    @Override public void onInitializeClient() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(class_2960.method_60655("tomermodhub", "chest_esp"), (draw, timer) -> chestEsp.render(draw));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            // Compile against the REAL Brigadier classes supplied by Minecraft.
            // Do not compile this against hand-made dummy classes; that caused
            // NoSuchMethodError in older distributed jars.
            dispatcher.register(literal("modhub")
                .executes(ctx -> { openGui(); return 1; })
                .then(literal("gui").executes(ctx -> { openGui(); return 1; }))
                .then(literal("stop").executes(ctx -> {
                    stopRtp("Stopped by player");
                    stopNavigation();
                    return 1;
                })));
            dispatcher.register(literal("rtpbot")
                .then(literal("gui").executes(ctx -> { openGui(); return 1; }))
                .then(literal("start").executes(ctx -> { startRtp(); return 1; }))
                .then(literal("stop").executes(ctx -> {
                    stopRtp("Stopped by player");
                    return 1;
                })));
        });
    }

    public void screenClosedWithShift() { priorKey = true; }

    public void openGui() {
        class_310 mc = class_310.method_1551();
        if (mc != null) mc.method_1507(new ModHubScreen(this));
    }

    private class_746 player() { return client == null ? null : client.field_1724; }

    public String coords() {
        class_746 p = player();
        return p == null ? "Not in world" : "X: " + (int)Math.floor(p.method_23317()) + "  Z: " + (int)Math.floor(p.method_23321());
    }

    public void message(String s) {
        class_746 p = player();
        if (p != null) p.method_7353(class_2561.method_30163("[Mod Hub] " + s), false);
    }

    public boolean isChestEspEnabled() { return chestEspEnabled; }
    public boolean showEnderChests() { return enderChestsEnabled; }
    public int getChestEspRange() { return chestEspRange; }
    public int getChestCount() { return chestEsp.visibleCount(); }
    public void toggleChestEsp() { chestEspEnabled = !chestEspEnabled; save(); }
    public void toggleEnderChests() { enderChestsEnabled = !enderChestsEnabled; save(); }
    public boolean showShulkers() { return shulkersEnabled; }
    public boolean showTorches() { return torchesEnabled; }
    public void toggleShulkers() { shulkersEnabled = !shulkersEnabled; save(); }
    public void toggleTorches() { torchesEnabled = !torchesEnabled; chestEsp.resetTorchScan(); save(); }
    public boolean setChestEspRange(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= 8 && value <= 128) {
                chestEspRange = value; save(); return true;
            }
        } catch (NumberFormatException ignored) { }
        return false;
    }
    public boolean isRtpRunning() { return rtpRunning; }
    public boolean isNavRunning() { return navigator.isActive(); }
    public String rtpStatus() { return rtpStatus; }
    public String navStatus() { return navigator.isActive() ? navigator.status() : navStatus; }
    public int getAttempts() { return attempts; }
    public int getLimit() { return limit; }
    public int getNavX() { return navX; }
    public int getNavZ() { return navZ; }
    public String importStatus() { return importStatus; }
    public String getRegion() { return REGIONS[region]; }
    public boolean hasBaritone() { return true; } // now self-contained

    public void setLimit(String raw) {
        try {
            int parsed = Integer.parseInt(raw.trim().replace(",", ""));
            if (parsed < 1 || parsed > WORLD_MAX) {
                rtpStatus = "Enter a limit from 1 to " + WORLD_MAX;
                return;
            }
            limit = parsed;
            if (rtpRunning) stopRtp("Limit changed; restart RTP");
            rtpStatus = "Limit updated to +/-" + limit;
            save();
        } catch (NumberFormatException e) { rtpStatus = "Invalid limit - use a whole number"; }
    }

    public void cycleRegion() {
        if (rtpRunning) stopRtp("Region changed; restart RTP");
        region = (region + 1) % REGIONS.length;
        rtpStatus = "Selected " + REGIONS[region];
        save();
    }

    public boolean insideTarget(double x, double z) {
        return Math.abs(x) <= limit && Math.abs(z) <= limit;
    }

    public void startRtp() {
        class_746 p = player();
        if (p == null || client.method_1562() == null) {
            rtpStatus = "Join a multiplayer server first";
            return;
        }
        if (insideTarget(p.method_23317(), p.method_23321())) {
            rtpRunning = false;
            rtpStatus = "Already inside target (+/-" + limit + ")";
            return;
        }
        attempts = 0;
        missed = 0;
        rtpRunning = true;
        waiting = false;
        teleported = false;
        gapTicks = 0;
        sendRtp();
    }

    public void stopRtp(String reason) {
        boolean wasRunning = rtpRunning;
        rtpRunning = false;
        waiting = false;
        rtpStatus = reason;
        if (wasRunning) message("RTP " + reason + " after " + attempts + " attempts");
    }

    private void sendRtp() {
        class_746 p = player();
        class_634 network = client == null ? null : client.method_1562();
        if (!rtpRunning || p == null || network == null) {
            stopRtp("Disconnected");
            return;
        }
        if (insideTarget(p.method_23317(), p.method_23321())) {
            stopRtp("FOUND " + coords());
            return;
        }
        originX = lastX = p.method_23317();
        originZ = lastZ = p.method_23321();
        teleported = false;
        waiting = true;
        waitTicks = stableTicks = gapTicks = 0;
        attempts++;
        rtpStatus = "Attempt #" + attempts + " | " + getRegion();
        // Uses the selected RTP region as in the earlier Donut RTP mod.
        network.method_45730("rtp " + REGION_ARGUMENTS[region]);
    }

    private void tick(class_310 mc) {
        client = mc;
        if (mc == null) return;
        if (chestEspEnabled) chestEsp.update(mc);
        try {
            boolean key = GLFW.glfwGetKey(mc.method_22683().method_4490(), OPEN_KEY) == GLFW.GLFW_PRESS;
            if (key && !priorKey && mc.field_1755 == null) openGui();
            priorKey = key;
        } catch (Throwable ignored) { /* /modhub remains available */ }

        if (rtpRunning) {
            class_746 p = player();
            if (p == null || mc.field_1687 == null || mc.method_1562() == null) {
                stopRtp("Disconnected");
            } else if (insideTarget(p.method_23317(), p.method_23321())) {
                stopRtp("FOUND " + coords());
            } else if (waiting) {
                waitTicks++;
                double x = p.method_23317(), z = p.method_23321();
                if (!teleported && Math.hypot(x - originX, z - originZ) > TELEPORT_THRESHOLD) {
                    teleported = true;
                    stableTicks = 0;
                }
                if (teleported) {
                    if (Math.hypot(x - lastX, z - lastZ) < 0.15) stableTicks++;
                    else stableTicks = 0;
                    if (stableTicks >= SETTLE_TICKS && waitTicks > NEXT_RTP_GAP) {
                        waiting = false;
                        gapTicks = 0;
                        missed = 0;
                    }
                } else if (waitTicks >= TELEPORT_TIMEOUT) {
                    missed++;
                    if (missed >= MAX_MISSED_TELEPORTS) stopRtp("No teleport after 3 attempts; check RTP command");
                    else { waiting = false; gapTicks = 0; rtpStatus = "Teleport not detected, retrying"; }
                }
                lastX = x;
                lastZ = z;
            } else if (++gapTicks >= NEXT_RTP_GAP) sendRtp();
        }
        navigator.tick(mc);
    }

    /** Integrated walking controls, without any other mod. Stops on unsafe or blocked routes. */
    public void startNavigation(String rawX, String rawZ) {
        int x, z;
        try {
            x = Integer.parseInt(rawX.trim().replace(",", ""));
            z = Integer.parseInt(rawZ.trim().replace(",", ""));
            if (Math.abs((long)x) > WORLD_MAX || Math.abs((long)z) > WORLD_MAX) {
                navStatus = "Coordinates outside Minecraft limits";
                return;
            }
        } catch (NumberFormatException e) { navStatus = "Enter whole-number X and Z"; return; }
        if (player() == null) { navStatus = "Join a world before walking"; return; }
        if (rtpRunning) stopRtp("Stopped to navigate");
        navigator.start(client, x, z);
        navX=x;navZ=z;
        navStatus = "Walking toward X="+x+" Z="+z;
        message("Walking enabled; close UI with ESC or Right Shift");
        save();
    }
    public void stopNavigation() {
        navigator.stop(client);
        navStatus = "Walking stopped";
    }

    public String distanceToGoal() {
        class_746 p = player();
        if (p == null) return "-";
        return Long.toString(Math.round(Math.hypot(p.method_23317() - navX, p.method_23321() - navZ)));
    }

    public List<String> installedMods() {
        List<String> mods = new ArrayList<>();
        try {
            FabricLoader.getInstance().getAllMods().forEach(mod -> {
                mods.add(mod.getMetadata().getName() + " [" + mod.getMetadata().getId() + "]");
            });
            mods.sort(Comparator.naturalOrder());
        } catch (RuntimeException e) { mods.add("Could not read installed mods"); }
        return mods;
    }

    /** Opens the native Windows file picker. The selected mod is copied but not hot-loaded. */
    public void importModJar() {
        Thread chooser = new Thread(() -> {
            FileDialog dialog = null;
            try {
                dialog = new FileDialog((java.awt.Frame)null, "Import Fabric mod (.jar)", FileDialog.LOAD);
                dialog.setFile("*.jar");
                dialog.setVisible(true);
                String name = dialog.getFile();
                if (name == null) { importStatus = "Import cancelled"; return; }
                Path selected = Path.of(dialog.getDirectory(), name);
                if (!selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    importStatus = "Only .jar files are supported"; return;
                }
                try (ZipFile archive = new ZipFile(selected.toFile())) {
                    if (archive.getEntry("fabric.mod.json") == null) {
                        importStatus = "Not a Fabric mod (missing fabric.mod.json)"; return;
                    }
                }
                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                Files.createDirectories(mods);
                Path dest = mods.resolve(selected.getFileName());
                Files.copy(selected, dest);
                importStatus = "Imported " + dest.getFileName() + " - RESTART GAME";
            } catch (java.nio.file.FileAlreadyExistsException e) {
                importStatus = "This JAR already exists in mods folder";
            } catch (Exception e) {
                importStatus = "Import failed: " + e.getClass().getSimpleName();
            } finally {
                if (dialog != null) dialog.dispose();
            }
        }, "TomerModHub-ImportDialog");
        chooser.setDaemon(true);
        chooser.start();
    }

    public void openModsFolder() {
        try {
            Path folder = FabricLoader.getInstance().getGameDir().resolve("mods");
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
                message("Mods folder opened; restart Minecraft after adding a JAR");
            } else message("Open this folder manually: " + folder);
        } catch (Exception e) { message("Couldn't open folder; use your Modrinth profile's Open Folder option"); }
    }

    private Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("tomer-modhub.properties"); }

    private void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(configFile())) {
            p.load(in);
            limit = Math.max(1, Math.min(WORLD_MAX, Integer.parseInt(p.getProperty("rtp_limit", "50000"))));
            region = Math.floorMod(Integer.parseInt(p.getProperty("rtp_region", "0")), REGIONS.length);
            chestEspEnabled = Boolean.parseBoolean(p.getProperty("esp_enabled", "false"));
            enderChestsEnabled = Boolean.parseBoolean(p.getProperty("esp_ender", "true"));
            shulkersEnabled = Boolean.parseBoolean(p.getProperty("esp_shulker", "true"));
            torchesEnabled = Boolean.parseBoolean(p.getProperty("esp_torch", "false"));
            chestEspRange = Math.max(8, Math.min(128, Integer.parseInt(p.getProperty("esp_range", "64"))));
            navX = Integer.parseInt(p.getProperty("nav_x", "0"));
            navZ = Integer.parseInt(p.getProperty("nav_z", "0"));
        } catch (Exception ignored) { /* first launch */ }
    }

    private void save() {
        Properties p = new Properties();
        p.setProperty("rtp_limit", Integer.toString(limit));
        p.setProperty("rtp_region", Integer.toString(region));
        p.setProperty("nav_x", Integer.toString(navX));
        p.setProperty("nav_z", Integer.toString(navZ));
        p.setProperty("esp_enabled", Boolean.toString(chestEspEnabled));
        p.setProperty("esp_ender", Boolean.toString(enderChestsEnabled));
        p.setProperty("esp_shulker", Boolean.toString(shulkersEnabled));
        p.setProperty("esp_torch", Boolean.toString(torchesEnabled));
        p.setProperty("esp_range", Integer.toString(chestEspRange));
        try {
            Files.createDirectories(configFile().getParent());
            try (OutputStream out = Files.newOutputStream(configFile())) { p.store(out, "Tomer Mod Hub settings"); }
        } catch (Exception e) { message("Could not save settings: " + e.getMessage()); }
    }
}