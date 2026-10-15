package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.hud.TextHud;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Draw;

import java.util.List;
import java.util.Locale;

/** The classic PvP-client mods: reach, combo, compass, stopwatch, day counter and friends. */
public final class Lunarish {
    private Lunarish() {}

    /** Distance of your last hit on a player or mob. */
    public static final class Reach extends TextHud {
        final Setting.Slider decimals;
        final Setting.Slider hideAfter;
        private double last = -1;
        private long at;

        public Reach() {
            super("reach", "Reach display", "How far away you were on your last hit.", Category.COMBAT, false, 0.5, 0.62);
            decimals = add(new Setting.Slider("decimals", "Decimal places", 2, 0, 3, 1, ""));
            decimals.in("Text");
            hideAfter = add(new Setting.Slider("hide_after", "Hide after", 3, 0, 30, 1, "s"));
            hideAfter.in("Text").hint("0 keeps it on screen.");
        }

        public void hit(double distance) {
            last = distance;
            at = System.currentTimeMillis();
        }

        @Override
        public boolean active(Client c) {
            if (editing) return true;
            if (last < 0) return false;
            return hideAfter.value == 0 || System.currentTimeMillis() - at < hideAfter.value * 1000;
        }

        protected void rows(Client c, List<Row> out) {
            double v = editing && last < 0 ? 2.87 : last;
            out.add(new Row("Reach", String.format(Locale.US, "%." + (int) decimals.value + "f", v) + " blocks"));
        }
    }

    /** Hits in a row without taking damage. */
    public static final class Combo extends TextHud {
        final Setting.Slider timeout;
        final Setting.Toggle hideZero;
        private int count;
        private long lastHit;

        public Combo() {
            super("combo", "Combo counter", "Hits in a row before you get hit or stop attacking.", Category.COMBAT, false, 0.5, 0.67);
            timeout = add(new Setting.Slider("timeout", "Reset after", 2, 1, 5, 0.5, "s"));
            timeout.in("Text");
            hideZero = add(new Setting.Toggle("hide_zero", "Hide when 0", true));
            hideZero.in("Text");
        }

        public void hit() {
            long now = System.currentTimeMillis();
            if (now - lastHit > timeout.value * 1000) count = 0;
            count++;
            lastHit = now;
        }

        @Override
        public void tick(Client c) {
            if (c.game.inWorld() && c.game.hurtTime() > 0) count = 0;
            if (System.currentTimeMillis() - lastHit > timeout.value * 1000) count = 0;
        }

        @Override
        public boolean active(Client c) {
            return editing || !hideZero.value || count > 0;
        }

        protected void rows(Client c, List<Row> out) {
            int n = editing && count == 0 ? 4 : count;
            out.add(new Row("Combo", n + (n == 1 ? " hit" : " hits")));
        }
    }

    /** A compass ribbon along the top of the screen. */
    public static final class Compass extends HudModule {
        final Setting.Slider span;
        final Setting.Slider widthPx;
        final Setting.Toggle degrees;
        final Setting.Toggle accentNorth;

        public Compass() {
            super("compass", "Compass", "A compass bar showing which way you're facing.", Category.HUD, false, 0.5, 0.0);
            widthPx = add(new Setting.Slider("width", "Width", 180, 100, 320, 10, "px"));
            widthPx.in("Compass");
            span = add(new Setting.Slider("span", "Field shown", 180, 90, 360, 15, "°"));
            span.in("Compass");
            degrees = add(new Setting.Toggle("degrees", "Show degrees", true));
            degrees.in("Compass");
            accentNorth = add(new Setting.Toggle("accent_north", "Highlight north", true));
            accentNorth.in("Compass");
        }

        @Override
        protected int[] measure(Gfx g, Client c, Style s) {
            return new int[]{(int) widthPx.value, degrees.value ? 20 : 11};
        }

        @Override
        protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
            // Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east. Compass heading: 0 = north.
            double heading = ((c.game.yaw() + 180) % 360 + 360) % 360;
            double pxPerDeg = w / span.value;
            String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            for (int deg = 0; deg < 360; deg += 15) {
                double diff = ((deg - heading + 540) % 360) - 180;
                if (Math.abs(diff) > span.value / 2) continue;
                int x = (int) Math.round(w / 2.0 + diff * pxPerDeg);
                double fade = 1 - Math.abs(diff) / (span.value / 2);
                if (deg % 45 == 0) {
                    String n = names[deg / 45];
                    int col = deg == 0 && accentNorth.value ? s.accent : (deg % 90 == 0 ? s.value : s.label);
                    g.text(n, x - g.textWidth(n) / 2, 0, Draw.alpha(col, 0.35 + 0.65 * fade), s.shadow);
                } else {
                    g.rect(x, 3, x + 1, 6, Draw.alpha(s.label, 0.25 + 0.5 * fade));
                }
            }
            g.rect(w / 2, 9, w / 2 + 1, 11, s.accent);
            if (degrees.value) {
                String d = (int) Math.round(heading) % 360 + "°";
                g.text(d, w / 2 - g.textWidth(d) / 2, 12, s.value, s.shadow);
            }
        }
    }

    /** Start/stop timer on a key. */
    public static final class Stopwatch extends TextHud {
        public final Setting.KeyBind toggleKey;
        public final Setting.KeyBind resetKey;
        private long startedAt, elapsed;
        private boolean running;

        public Stopwatch() {
            super("stopwatch", "Stopwatch", "Start, stop and reset a timer with keys of your choice.", Category.HUD, false, 1.0, 0.09);
            toggleKey = add(new Setting.KeyBind("toggle_key", "Start / stop key", null));
            toggleKey.in("Controls").hint("Pick keys you don't use. Right-click a key button to clear it.");
            resetKey = add(new Setting.KeyBind("reset_key", "Reset key", null));
            resetKey.in("Controls");
        }

        public void toggle() {
            long now = System.currentTimeMillis();
            if (running) elapsed += now - startedAt;
            else startedAt = now;
            running = !running;
        }

        public void reset() {
            running = false;
            elapsed = 0;
        }

        private long ms() {
            return elapsed + (running ? System.currentTimeMillis() - startedAt : 0);
        }

        protected void rows(Client c, List<Row> out) {
            long t = ms();
            String v = String.format(Locale.US, "%d:%02d.%02d", t / 60000, (t / 1000) % 60, (t / 10) % 100);
            out.add(new Row("Timer", v, running ? 0 : BasicHud.OK));
        }
    }

    public static final class DayCounter extends TextHud {
        public DayCounter() {
            super("day_counter", "Day counter", "The current in-game day.", Category.HUD, false, 1.0, 0.13);
        }

        protected void rows(Client c, List<Row> out) {
            long t = c.game.dayTime();
            out.add(new Row("Day", t < 0 ? "--" : String.valueOf(t / 24000 + 1)));
        }
    }

    public static final class ServerAddress extends TextHud {
        final Setting.Toggle hideSingleplayer;

        public ServerAddress() {
            super("server_address", "Server address", "The server you're playing on.", Category.HUD, false, 1.0, 0.17);
            hideSingleplayer = add(new Setting.Toggle("hide_sp", "Hide in singleplayer", true));
            hideSingleplayer.in("Text");
        }

        @Override
        public boolean active(Client c) {
            return editing || !hideSingleplayer.value || c.game.serverAddress() != null;
        }

        protected void rows(Client c, List<Row> out) {
            String a = c.game.serverAddress();
            out.add(new Row("Server", a != null ? a : editing ? "mc.hypixel.net" : "Singleplayer"));
        }
    }

    public static final class Saturation extends TextHud {
        public Saturation() {
            super("saturation", "Saturation", "Hidden saturation, so you know when you'll get hungry.", Category.HUD, false, 0.5, 0.80);
        }

        protected void rows(Client c, List<Row> out) {
            float s = c.game.saturation();
            int col = s <= 0 ? BasicHud.BAD : s < 4 ? BasicHud.OK : 0;
            out.add(new Row("Saturation", String.format(Locale.US, "%.1f", Math.max(0, s)), col));
        }
    }
}
