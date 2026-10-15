package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.hud.TextHud;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.sb.CoinTracker;
import dev.skylaunch.client.util.Format;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Second batch of mods: camera tweaks, vanilla HUD hiding, chat timestamps and more HUD widgets. */
public final class Extras {
    private Extras() {}

    // ---------------------------------------------------------------- camera

    /** Stops the screen bobbing up and down while you walk. The version layer applies it. */
    public static final class NoViewBob extends Module {
        public NoViewBob() {
            super("no_view_bob", "No view bobbing", "Stop the camera bobbing while you walk.", Category.VISUAL, true);
        }
    }

    /** Stops the camera tilting when you take damage. */
    public static final class NoHurtCam extends Module {
        public NoHurtCam() {
            super("no_hurt_cam", "No hurt camera", "Stop the screen shaking when you take damage.", Category.VISUAL, false);
        }
    }

    /** Hides the fire overlay that covers half the screen while burning. */
    public static final class NoFireOverlay extends Module {
        public NoFireOverlay() {
            super("no_fire_overlay", "No fire overlay", "Hide the flames that cover your screen while burning.", Category.VISUAL, false);
        }
    }

    /** Hide parts of the vanilla HUD that the SkyBlock widgets replace. */
    public static final class HideVanillaHud extends Module {
        public final Setting.Toggle hearts, armor, food, xp, sidebar, bossBar;
        /** Set by the version layer: whether it can hide the sidebar. */
        public static boolean canHideSidebar = true;

        public HideVanillaHud() {
            super("hide_vanilla_hud", "Hide vanilla HUD", "Hide hearts, armor, hunger, XP or the sidebar. Pairs well with the SkyBlock bars.", Category.HUD, false);
            hearts = add(new Setting.Toggle("hearts", "Hearts", true));
            armor = add(new Setting.Toggle("armor", "Armor", true));
            food = add(new Setting.Toggle("food", "Hunger", true));
            xp = add(new Setting.Toggle("xp", "Experience bar", false));
            sidebar = add(new Setting.Toggle("sidebar", "Sidebar (scoreboard)", false));
            sidebar.hint("SkyBlock info keeps working with the sidebar hidden.");
            sidebar.showIf(() -> canHideSidebar);
            bossBar = add(new Setting.Toggle("boss_bar", "Boss bar", false));
        }

        public boolean hide(Setting.Toggle part) {
            return enabled && part.value;
        }
    }

    // ---------------------------------------------------------------- chat

    public static final class ChatTimestamps extends Module {
        public final Setting.Toggle twentyFour, seconds;
        public final Setting.Choice colour;

        /** Formatting code and RGB for each colour choice (1.8.9 only has the code colours). */
        private static final char[] CODES = {'7', '8', 'f', 'b'};
        private static final int[] RGB = {0xAAAAAA, 0x555555, 0xFFFFFF, 0x55FFFF};

        public ChatTimestamps() {
            super("chat_timestamps", "Chat timestamps", "Show the time next to every chat message.", Category.COMBAT, false);
            twentyFour = add(new Setting.Toggle("24h", "24-hour clock", false));
            seconds = add(new Setting.Toggle("seconds", "Show seconds", false));
            colour = add(new Setting.Choice("colour", "Colour", 0, "Gray", "Dark gray", "White", "Aqua"));
        }

        /** e.g. "[8:41 PM] ". */
        public String stamp(Date when) {
            String p = (twentyFour.value ? "HH:mm" : "h:mm") + (seconds.value ? ":ss" : "") + (twentyFour.value ? "" : " a");
            return "[" + new SimpleDateFormat(p, Locale.US).format(when) + "] ";
        }

        public char code() {
            return CODES[colour.index];
        }

        public int rgb() {
            return RGB[colour.index];
        }
    }

    // ---------------------------------------------------------------- HUD widgets

    public static final class Speed extends TextHud {
        final Setting.Toggle vertical;
        final Setting.Choice unit;

        public Speed() {
            super("speed", "Speed", "How fast you're moving.", Category.HUD, false, 0.0, 0.30);
            vertical = add(new Setting.Toggle("vertical", "Include up and down", false));
            vertical.in("Text");
            unit = add(new Setting.Choice("unit", "Unit", 0, "Blocks/s", "Speed stat"));
            unit.in("Text").hint("Speed stat shows SkyBlock's Speed number (100 = walking).");
        }

        protected void rows(Client c, List<Row> out) {
            double bps = vertical.value ? c.motion.speed3d() : c.motion.speed2d();
            if (unit.is("Speed stat")) {
                // Walking without sprint is about 4.317 blocks/s at 100 Speed.
                out.add(new Row("Speed", String.valueOf(Math.round(bps / 4.317 * 100))));
            } else {
                out.add(new Row("Speed", String.format(Locale.US, "%.2f b/s", bps)));
            }
        }
    }

    public static final class Memory extends TextHud {
        final Setting.Choice style;

        public Memory() {
            super("memory", "Memory", "How much RAM Minecraft is using.", Category.HUD, false, 0.0, 0.35);
            style = add(new Setting.Choice("style", "Show", 0, "Used / max", "Percent"));
            style.in("Text");
        }

        protected void rows(Client c, List<Row> out) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
            int pct = (int) Math.round(used * 100.0 / max);
            int col = pct >= 90 ? BasicHud.BAD : pct >= 75 ? BasicHud.OK : 0;
            String v = style.is("Percent") ? pct + "%" : String.format(Locale.US, "%.1f / %.1f GB", used / 1073741824.0, max / 1073741824.0);
            out.add(new Row("Memory", v, col));
        }
    }

    public static final class SessionTimer extends TextHud {
        public SessionTimer() {
            super("session_timer", "Session timer", "How long you've been playing this session.", Category.HUD, false, 1.0, 0.04);
        }

        protected void rows(Client c, List<Row> out) {
            long ms = c.playStartedAt == 0 ? 0 : System.currentTimeMillis() - c.playStartedAt;
            long s = ms / 1000;
            out.add(new Row("Playing", String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)));
        }
    }

    /** Coins earned this session and per hour, from purse changes. */
    public static final class Coins extends TextHud {
        final Setting.Toggle perHour, session, gainsOnly;
        final Setting.Choice numbers;
        public final Setting.KeyBind resetKey;

        public Coins() {
            super("sb_coins", "Coin tracker", "Coins made this session and per hour, from your purse.", Category.SKYBLOCK, false, 1.0, 0.40);
            session = add(new Setting.Toggle("session", "Coins this session", true));
            session.in("Content");
            perHour = add(new Setting.Toggle("per_hour", "Coins per hour", true));
            perHour.in("Content");
            gainsOnly = add(new Setting.Toggle("gains_only", "Only count gains", false));
            gainsOnly.in("Content").hint("Ignores spending, so buying things doesn't lower your rate.");
            numbers = add(new Setting.Choice("numbers", "Numbers", 1, "Full", "Compact"));
            numbers.in("Content");
            resetKey = add(new Setting.KeyBind("reset_key", "Reset key", null));
            resetKey.in("Content").hint("Unbound by default. Right-click a key button to clear it.");
            layout.setDefault("Columns");
        }

        @Override
        public boolean active(Client c) {
            return SkyBlockHud.show(c, this);
        }

        protected void rows(Client c, List<Row> out) {
            CoinTracker t = c.coins;
            long now = System.currentTimeMillis();
            boolean sample = editing && !t.started();
            double total = sample ? 2_450_000 : gainsOnly.value ? t.gained() : t.net();
            double rate = sample ? 1_830_000 : t.perHour(now, gainsOnly.value);
            boolean compact = numbers.is("Compact");
            int coin = 0xFFFFD866;
            if (session.value) out.add(new Row("Session", (total >= 0 ? "+" : "") + Format.number(total, compact), total < 0 ? BasicHud.BAD : coin));
            if (perHour.value) out.add(new Row("Per hour", Format.number(rate, compact), coin));
        }
    }
}
