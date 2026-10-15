package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Game;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.hud.TextHud;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Armor status, potion effects, zoom and toggle sprint. */
public final class CombatQol {
    private CombatQol() {}

    public static final class Armor extends HudModule {
        final Setting.Choice direction;
        final Setting.Choice durability;
        final Setting.Toggle held;

        public Armor() {
            super("armor", "Armor status", "Your armor and held item with durability.", Category.COMBAT, false, 1.0, 0.75);
            direction = add(new Setting.Choice("direction", "Direction", 0, "Vertical", "Horizontal"));
            direction.in("Layout");
            durability = add(new Setting.Choice("durability", "Durability", 0, "Percent", "Remaining", "Hidden"));
            durability.in("Layout");
            held = add(new Setting.Toggle("held", "Include held item", true));
            held.in("Layout");
            background.setDefault("Off");
        }

        private List<Game.ItemInfo> items(Client c) {
            List<Game.ItemInfo> all = c.game.armorAndHeld();
            if (held.value) return all;
            List<Game.ItemInfo> armor = new ArrayList<Game.ItemInfo>();
            for (Game.ItemInfo it : all) if (!it.held) armor.add(it);
            return armor;
        }

        private String label(Game.ItemInfo it) {
            if (it.maxDamage <= 0) return it.count > 1 ? String.valueOf(it.count) : "";
            int left = it.maxDamage - it.damage;
            if (durability.is("Percent")) return Math.round(left * 100.0 / it.maxDamage) + "%";
            if (durability.is("Remaining")) return String.valueOf(left);
            return "";
        }

        protected int[] measure(Gfx g, Client c, Style s) {
            List<Game.ItemInfo> its = items(c);
            int n = editing && its.isEmpty() ? 4 : its.size();
            int textW = 0;
            for (Game.ItemInfo it : its) textW = Math.max(textW, g.textWidth(label(it)));
            if (editing && its.isEmpty()) textW = g.textWidth("100%");
            if (direction.is("Vertical")) return new int[]{16 + (textW > 0 ? 3 + textW : 0), n * 17 - 1};
            return new int[]{n * (16 + 3 + textW) - 3, 16};
        }

        protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
            List<Game.ItemInfo> its = items(c);
            if (editing && its.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    int y = i * 17;
                    g.rect(0, y, 16, y + 16, 0x40FFFFFF);
                    g.text("100%", 19, y + 4, s.value, s.shadow);
                }
                return;
            }
            int x = 0, y = 0, textW = 0;
            for (Game.ItemInfo it : its) textW = Math.max(textW, g.textWidth(label(it)));
            for (Game.ItemInfo it : its) {
                g.item(it.handle, x, y);
                String l = label(it);
                int col = s.value;
                if (it.maxDamage > 0) {
                    double f = (it.maxDamage - it.damage) / (double) it.maxDamage;
                    if (f < 0.25) col = BasicHud.BAD;
                    else if (f < 0.5) col = BasicHud.OK;
                }
                if (!l.isEmpty()) g.text(l, x + 19, y + 4, col, s.shadow);
                if (direction.is("Vertical")) y += 17;
                else x += 16 + 3 + textW;
            }
        }
    }

    public static final class Effects extends TextHud {
        final Setting.Toggle level;

        public Effects() {
            super("effects", "Potion effects", "Active effects with time left.", Category.COMBAT, false, 1.0, 0.45);
            level = add(new Setting.Toggle("level", "Show level", true));
            level.in("Text");
            layout.setDefault("Columns");
        }

        protected void rows(Client c, List<Row> out) {
            List<Game.EffectInfo> list = c.game.effects();
            if (editing && list.isEmpty()) {
                out.add(new Row("Speed II", "1:30"));
                out.add(new Row("Strength", "0:45"));
                return;
            }
            for (Game.EffectInfo e : list) {
                String name = e.name + (level.value && e.amplifier > 0 ? " " + Format.roman(e.amplifier + 1) : "");
                int col = e.durationTicks >= 0 && e.durationTicks < 200 ? BasicHud.BAD : 0;
                out.add(new Row(name, Format.ticks(e.durationTicks), col));
            }
        }

        @Override
        public boolean active(Client c) {
            return editing || !c.game.effects().isEmpty();
        }
    }

    /** Hold a key to zoom. The version layer divides the camera FOV by {@link #divisor(float)}. */
    public static final class Zoom extends Module {
        public final Setting.KeyBind key;
        final Setting.Slider amount;
        final Setting.Toggle smooth;
        final Setting.Toggle scroll;
        public final Setting.Toggle cinematic;

        private double target = 1, current = 1, previous = 1;
        private double scrollFactor = 1;

        public Zoom() {
            super("zoom", "Zoom", "Hold a key to zoom in. Scroll while zooming to adjust.", Category.COMBAT, true);
            key = add(new Setting.KeyBind("key", "Zoom key", "C"));
            amount = add(new Setting.Slider("amount", "Zoom", 4, 1.5, 12, 0.5, "x"));
            smooth = add(new Setting.Toggle("smooth", "Smooth", true));
            scroll = add(new Setting.Toggle("scroll", "Scroll to adjust", true));
            cinematic = add(new Setting.Toggle("cinematic", "Smooth camera while zoomed", false));
        }

        public boolean zooming(Client c) {
            return enabled && c.game.inWorld() && key.code != -1 && c.game.keyDown(key.code) && c.screenOpen == null;
        }

        @Override
        public void tick(Client c) {
            boolean on = zooming(c);
            if (!on) scrollFactor = 1;
            target = on ? amount.value * scrollFactor : 1;
            previous = current;
            current = smooth.value ? current + (target - current) * 0.5 : target;
            if (Math.abs(current - target) < 0.01) current = target;
        }

        /** Returns true when the scroll was used for zooming. */
        public boolean scrolled(Client c, double amountScrolled) {
            if (!scroll.value || !zooming(c) || amountScrolled == 0) return false;
            scrollFactor = Math.max(0.25, Math.min(4, scrollFactor * (amountScrolled > 0 ? 1.15 : 1 / 1.15)));
            return true;
        }

        /** FOV divisor for this frame (1 = no zoom). */
        public double divisor(float partialTicks) {
            return previous + (current - previous) * partialTicks;
        }
    }

    /** Sprint stays on after one press. The version layer holds the sprint key while toggled. */
    public static final class ToggleSprint extends TextHud {
        public final Setting.KeyBind key;
        final Setting.Toggle showText;
        public boolean toggled;

        public ToggleSprint() {
            super("toggle_sprint", "Toggle sprint", "Press once to keep sprinting.", Category.COMBAT, false, 0.0, 0.97);
            key = add(new Setting.KeyBind("key", "Toggle key", "V"));
            key.in("Controls");
            showText = add(new Setting.Toggle("show_text", "Show status on screen", true));
            showText.in("Controls");
            labels.setDefault(false);
            background.setDefault("Off");
            shadow.setDefault("On");
        }

        public void pressed() {
            toggled = !toggled;
        }

        @Override
        protected void onDisable() {
            toggled = false;
        }

        @Override
        public boolean active(Client c) {
            return showText.value && (toggled || editing);
        }

        protected void rows(Client c, List<Row> out) {
            out.add(new Row(null, c.game.sprinting() || editing ? "Sprinting (toggled)" : "Sprint toggled"));
        }
    }

    public static List<Module> all() {
        List<Module> l = new ArrayList<Module>();
        l.add(new Armor());
        l.add(new Effects());
        l.add(new Zoom());
        l.add(new ToggleSprint());
        return l;
    }
}
