package dev.skylaunch.client;

import java.util.List;

/**
 * Everything the shared code needs to read from (or ask of) the running game.
 * Implemented once per Minecraft version.
 */
public interface Game {
    // --- world / player -----------------------------------------------------
    boolean inWorld();

    int fps();

    /** Round-trip ping to the server in ms, or -1 when unknown (singleplayer). */
    int ping();

    double x();

    double y();

    double z();

    /** Player yaw in degrees as Minecraft reports it. */
    float yaw();

    boolean sprinting();

    /** Armor from helmet to boots, then the held item (held = true). Empty slots are omitted. */
    List<ItemInfo> armorAndHeld();

    List<EffectInfo> effects();

    // --- Hypixel data -------------------------------------------------------
    /** Sidebar title with formatting codes removed, or "" when there is no sidebar. */
    String scoreboardTitle();

    /** Sidebar lines, top to bottom, formatting codes removed. */
    List<String> scoreboardLines();

    /** Tab list entries, formatting codes removed. */
    List<String> tabLines();

    // --- input --------------------------------------------------------------
    /** Is this key (in this version's own key-code scheme) held right now? */
    boolean keyDown(int keyCode);

    /** Readable key name, e.g. "Right Shift". */
    String keyName(int keyCode);

    /** Key code for a named key ("RIGHT_SHIFT", "C", "V", "W"...), in this version's scheme. */
    int key(String name);

    boolean forwardDown();

    boolean backDown();

    boolean leftDown();

    boolean rightDown();

    boolean jumpDown();

    boolean attackDown();

    boolean useDown();

    // --- screens --------------------------------------------------------------
    void openScreen(dev.skylaunch.client.ui.UiScreen screen);

    void closeScreen();

    // --- persistence ----------------------------------------------------------
    /** Folder for skylaunch-client.json (the profile's config folder). */
    java.io.File configDir();

    /** Version label shown in the menu footer, e.g. "26.2 Fabric". */
    String platformName();

    // --- world sharing --------------------------------------------------------
    /** True while playing your own singleplayer world. */
    boolean inSingleplayer();

    /** Why sharing isn't possible right now, or null when it is. */
    String shareUnavailableReason();

    /** Opens Minecraft's "Open to LAN" screen. Returns null on success, or a message. */
    String startSharing();

    /** True once the world has been opened to LAN. */
    boolean worldShared();

    void copyToClipboard(String text);

    String playerName();

    /** The profile's game folder (where skylaunch-share.json lives). */
    java.io.File gameDir();

    final class ItemInfo {
        public final Object handle;
        public final int damage;
        public final int maxDamage;
        public final int count;
        public final boolean held;

        public ItemInfo(Object handle, int damage, int maxDamage, int count, boolean held) {
            this.handle = handle;
            this.damage = damage;
            this.maxDamage = maxDamage;
            this.count = count;
            this.held = held;
        }
    }

    final class EffectInfo {
        public final String name;
        public final int amplifier;
        /** Remaining duration in ticks, or -1 for infinite. */
        public final int durationTicks;

        public EffectInfo(String name, int amplifier, int durationTicks) {
            this.name = name;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
        }
    }
}
