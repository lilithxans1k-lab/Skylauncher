package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.hud.TextHud;
import dev.skylaunch.client.module.Setting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** The everyday text widgets: FPS, CPS, ping, coordinates, clock. */
public final class BasicHud {
    private BasicHud() {}

    static final int GOOD = 0xFF7BD88F, OK = 0xFFFFD866, BAD = 0xFFFF6B6B;

    public static final class Fps extends TextHud {
        final Setting.Toggle colourCode;

        public Fps() {
            super("fps", "FPS", "Frames per second.", Category.HUD, true, 0.0, 0.0);
            colourCode = add(new Setting.Toggle("colour_code", "Colour by value", false));
            colourCode.in("Text");
        }

        protected void rows(Client c, List<Row> out) {
            int fps = c.game.fps();
            int col = !colourCode.value ? 0 : fps >= 60 ? GOOD : fps >= 30 ? OK : BAD;
            out.add(new Row("FPS", String.valueOf(fps), col));
        }
    }

    public static final class Cps extends TextHud {
        final Setting.Toggle right;

        public Cps() {
            super("cps", "CPS", "Clicks per second for each mouse button.", Category.HUD, false, 0.0, 0.04);
            right = add(new Setting.Toggle("right", "Show right clicks", true));
            right.in("Text");
        }

        protected void rows(Client c, List<Row> out) {
            String v = right.value ? c.cps.left() + " | " + c.cps.right() : String.valueOf(c.cps.left());
            out.add(new Row("CPS", v));
        }
    }

    public static final class Ping extends TextHud {
        final Setting.Toggle colourCode;

        public Ping() {
            super("ping", "Ping", "Connection latency to the server.", Category.HUD, false, 0.0, 0.08);
            colourCode = add(new Setting.Toggle("colour_code", "Colour by value", true));
            colourCode.in("Text");
        }

        protected void rows(Client c, List<Row> out) {
            int p = c.game.ping();
            if (p < 0) {
                out.add(new Row("Ping", "--"));
                return;
            }
            int col = !colourCode.value ? 0 : p < 80 ? GOOD : p < 160 ? OK : BAD;
            out.add(new Row("Ping", p + " ms", col));
        }
    }

    public static final class Coords extends TextHud {
        final Setting.Choice style;
        final Setting.Toggle facing;
        final Setting.Toggle decimals;

        public Coords() {
            super("coords", "Coordinates", "Your position and the direction you face.", Category.HUD, true, 0.0, 0.13);
            style = add(new Setting.Choice("style", "Style", 0, "One per line", "Single line"));
            style.in("Text");
            facing = add(new Setting.Toggle("facing", "Show direction", true));
            facing.in("Text");
            decimals = add(new Setting.Toggle("decimals", "One decimal place", false));
            decimals.in("Text");
        }

        private String n(double v) {
            return decimals.value ? String.format(java.util.Locale.US, "%.1f", v) : String.valueOf((long) Math.floor(v));
        }

        protected void rows(Client c, List<Row> out) {
            if (style.is("Single line")) {
                out.add(new Row("XYZ", n(c.game.x()) + " " + n(c.game.y()) + " " + n(c.game.z())));
            } else {
                out.add(new Row("X", n(c.game.x())));
                out.add(new Row("Y", n(c.game.y())));
                out.add(new Row("Z", n(c.game.z())));
            }
            if (facing.value) out.add(new Row("Facing", direction(c.game.yaw())));
        }

        static String direction(float yaw) {
            float y = ((yaw % 360) + 360) % 360;
            String[] names = {"South (+Z)", "South-west", "West (-X)", "North-west", "North (-Z)", "North-east", "East (+X)", "South-east"};
            int idx = (int) Math.floor((y + 22.5) / 45) % 8;
            return names[idx];
        }
    }

    public static final class Clock extends TextHud {
        final Setting.Toggle twentyFour;
        final Setting.Toggle seconds;

        public Clock() {
            super("clock", "Clock", "Your computer's time.", Category.HUD, false, 1.0, 0.0);
            twentyFour = add(new Setting.Toggle("24h", "24-hour clock", false));
            twentyFour.in("Text");
            seconds = add(new Setting.Toggle("seconds", "Show seconds", false));
            seconds.in("Text");
            labels.setDefault(false);
        }

        protected void rows(Client c, List<Row> out) {
            String p = (twentyFour.value ? "HH:mm" : "h:mm") + (seconds.value ? ":ss" : "") + (twentyFour.value ? "" : " a");
            out.add(new Row("Time", new SimpleDateFormat(p).format(new Date())));
        }
    }
}
