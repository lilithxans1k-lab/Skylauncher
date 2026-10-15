package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.hud.TextHud;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.sb.SkyBlock;
import dev.skylaunch.client.util.Draw;
import dev.skylaunch.client.util.Format;

import java.util.ArrayList;
import java.util.List;

/** SkyBlock widgets. Data comes from {@link SkyBlock}, which reads the sidebar, tab list and action bar. */
public final class SkyBlockHud {
    private SkyBlockHud() {}

    static boolean show(Client c, HudModule m) {
        return m.editing || c.sb.onSkyBlock || !c.appearance.onlyInSkyBlock.value;
    }

    /** Purse, bits, bank, location and other sidebar/tab info, each line switchable. */
    public static final class Stats extends TextHud {
        final Setting.Toggle purse, bits, bank, motes, copper, location, server, profile, date, time;
        final Setting.Choice numbers;
        final Setting.Toggle coloured;
        final Setting.Color coinColour, bitsColour;

        public Stats() {
            super("sb_stats", "SkyBlock info", "Purse, bits, bank, location and more, each line on or off.", Category.SKYBLOCK, true, 1.0, 0.22);
            purse = line("purse", "Purse", true);
            bits = line("bits", "Bits", true);
            bank = line("bank", "Bank", true);
            motes = line("motes", "Motes (Rift)", true);
            copper = line("copper", "Copper (Garden)", true);
            location = line("location", "Location", true);
            server = line("server", "Server", false);
            profile = line("profile", "Profile", false);
            date = line("date", "SkyBlock date", false);
            time = line("time", "SkyBlock time", false);
            numbers = add(new Setting.Choice("numbers", "Numbers", 0, "Full", "Compact"));
            numbers.in("Text").hint("Compact shows 12.4M instead of 12,400,000.");
            coloured = add(new Setting.Toggle("coloured", "Colour coins and bits", true));
            coloured.in("Text");
            coinColour = add(new Setting.Color("coin_colour", "Coin colour", 0xFFFFD866, false));
            coinColour.in("Text").showIf(() -> coloured.value);
            bitsColour = add(new Setting.Color("bits_colour", "Bits colour", 0xFF6FE3F5, false));
            bitsColour.in("Text").showIf(() -> coloured.value);
            layout.setDefault("Columns");
        }

        private Setting.Toggle line(String id, String name, boolean on) {
            Setting.Toggle t = add(new Setting.Toggle("line_" + id, name, on));
            t.in("Lines");
            return t;
        }

        @Override
        public boolean active(Client c) {
            return show(c, this);
        }

        private String num(double v) {
            return Format.number(v, numbers.is("Compact"));
        }

        protected void rows(Client c, List<Row> out) {
            SkyBlock sb = c.sb;
            boolean sample = editing && !sb.onSkyBlock;
            int coin = coloured.value ? coinColour.argb : 0;
            int bitC = coloured.value ? bitsColour.argb : 0;
            if (purse.value && (sample || !Double.isNaN(sb.purse)))
                out.add(new Row(sample ? "Purse" : sb.purseLabel, num(sample ? 12_480_116 : sb.purse), coin));
            if (bits.value && (sample || !Double.isNaN(sb.bits))) out.add(new Row("Bits", num(sample ? 4_210 : sb.bits), bitC));
            if (bank.value && (sample || !Double.isNaN(sb.bank))) out.add(new Row("Bank", num(sample ? 250_000_000 : sb.bank), coin));
            if (motes.value && !Double.isNaN(sb.motes)) out.add(new Row("Motes", num(sb.motes), coloured.value ? 0xFFF59BD3 : 0));
            if (copper.value && !Double.isNaN(sb.copper)) out.add(new Row("Copper", num(sb.copper), coloured.value ? 0xFFE8A060 : 0));
            String loc = sample ? "Village" : !sb.location.isEmpty() ? sb.location : sb.area;
            if (location.value && !loc.isEmpty()) out.add(new Row("Location", loc));
            if (server.value && (sample || !sb.server.isEmpty())) out.add(new Row("Server", sample ? "m42AB" : sb.server));
            if (profile.value && (sample || !sb.profile.isEmpty())) out.add(new Row("Profile", sample ? "Apple" : sb.profile));
            if (date.value && (sample || !sb.date.isEmpty())) out.add(new Row("Date", sample ? "Late Spring 13th" : sb.date));
            if (time.value && (sample || !sb.time.isEmpty())) out.add(new Row("Time", sample ? "8:40pm" : sb.time));
        }
    }

    /** Health, mana, overflow and defense as clean bars. */
    public static final class Bars extends HudModule {
        final Setting.Toggle health, mana, overflow, defense;
        final Setting.Slider barWidth, barHeight;
        final Setting.Choice numbers, arrangement;
        final Setting.Color healthColour, absorbColour, manaColour, overflowColour, trackColour;

        private double shownHealth = 0, shownMana = 0;

        public Bars() {
            super("sb_bars", "Health & mana bars", "Health, mana and defense from the action bar as clean bars.", Category.SKYBLOCK, true, 0.5, 0.86);
            health = add(new Setting.Toggle("health", "Health", true));
            health.in("Show");
            mana = add(new Setting.Toggle("mana", "Mana", true));
            mana.in("Show");
            overflow = add(new Setting.Toggle("overflow", "Overflow mana", true));
            overflow.in("Show").showIf(() -> mana.value);
            defense = add(new Setting.Toggle("defense", "Defense", true));
            defense.in("Show");
            arrangement = add(new Setting.Choice("arrangement", "Arrangement", 1, "Stacked", "Side by side"));
            arrangement.in("Bars");
            barWidth = add(new Setting.Slider("width", "Bar width", 90, 40, 200, 2, "px"));
            barWidth.in("Bars");
            barHeight = add(new Setting.Slider("height", "Bar height", 4, 2, 12, 1, "px"));
            barHeight.in("Bars");
            numbers = add(new Setting.Choice("numbers", "Numbers", 0, "Above", "Hidden"));
            numbers.in("Bars");
            healthColour = add(new Setting.Color("health_colour", "Health", 0xFFFF5A5A, false));
            healthColour.in("Colours");
            absorbColour = add(new Setting.Color("absorb_colour", "Absorption", 0xFFFFB84D, false));
            absorbColour.in("Colours");
            manaColour = add(new Setting.Color("mana_colour", "Mana", 0xFF5AA9FF, false));
            manaColour.in("Colours");
            overflowColour = add(new Setting.Color("overflow_colour", "Overflow", 0xFF3FD0C9, false));
            overflowColour.in("Colours");
            trackColour = add(new Setting.Color("track_colour", "Empty part", 0x59FFFFFF, true));
            trackColour.in("Colours");
            background.setDefault("Off");
        }

        @Override
        public boolean active(Client c) {
            return editing || (show(c, this) && c.sb.statsFresh(System.currentTimeMillis()));
        }

        private double[] data(Client c) {
            SkyBlock sb = c.sb;
            if (editing && Double.isNaN(sb.health)) return new double[]{1450, 1250, 380, 520, 120, 412};
            return new double[]{sb.health, sb.maxHealth, sb.mana, sb.maxMana, sb.overflow, sb.defense};
        }

        private int barBlock(Gfx g) {
            return (numbers.is("Above") ? g.lineHeight() + 1 : 0) + (int) barHeight.value;
        }

        protected int[] measure(Gfx g, Client c, Style s) {
            int bw = (int) barWidth.value;
            int count = (health.value ? 1 : 0) + (mana.value ? 1 : 0);
            int block = barBlock(g);
            int w, h;
            if (arrangement.is("Side by side")) {
                w = count * bw + Math.max(0, count - 1) * 6;
                h = count > 0 ? block : 0;
            } else {
                w = bw;
                h = count * block + Math.max(0, count - 1) * 4;
            }
            if (defense.value) {
                int dw = g.textWidth("Defense 9999");
                if (count == 0) { w = dw; h = g.lineHeight() - 1; }
                else h += 3 + g.lineHeight() - 1;
                w = Math.max(w, dw);
            }
            return new int[]{w, h};
        }

        protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
            double[] d = data(c);
            shownHealth += (safe(d[0]) - shownHealth) * 0.35;
            shownMana += (safe(d[2]) - shownMana) * 0.35;
            int bw = (int) barWidth.value, block = barBlock(g);
            boolean side = arrangement.is("Side by side");
            int x = 0, y = 0;
            if (health.value) {
                bar(g, s, x, y, bw, shownHealth, d[1], Double.NaN,
                        d[0] > d[1] ? absorbColour.argb : healthColour.argb, 0,
                        Format.full(Math.round(safe(d[0]))) + "/" + Format.full(Math.round(safe(d[1]))), healthColour.argb);
                if (side) x += bw + 6;
                else y += block + 4;
            }
            if (mana.value) {
                double of = overflow.value ? d[4] : Double.NaN;
                String label = Format.full(Math.round(safe(d[2]))) + "/" + Format.full(Math.round(safe(d[3])))
                        + (!Double.isNaN(of) && of > 0 ? "  +" + Format.full(of) : "");
                bar(g, s, x, y, bw, shownMana, d[3], of, manaColour.argb, overflowColour.argb, label, manaColour.argb);
                if (!side) y += block + 4;
            }
            if (defense.value && !Double.isNaN(d[5])) {
                int dy = (health.value || mana.value) ? (side ? block + 3 : y - 1) : 0;
                g.text("Defense", 0, dy, s.label, s.shadow);
                g.text(Format.full(d[5]), g.textWidth("Defense "), dy, 0xFF7BD88F, s.shadow);
            }
        }

        private static double safe(double v) {
            return Double.isNaN(v) ? 0 : v;
        }

        private void bar(Gfx g, Style s, int x, int y, int w, double value, double max, double extra,
                         int colour, int extraColour, String label, int labelColour) {
            int bh = (int) barHeight.value;
            if (numbers.is("Above")) {
                g.text(label, x, y, labelColour, s.shadow);
                y += g.lineHeight() + 1;
            }
            int r = s.rounded ? Math.min(2, bh / 2) : 0;
            Draw.round(g, x, y, x + w, y + bh, r, trackColour.argb);
            double f = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
            int fw = (int) Math.round(w * f);
            if (fw > 0) Draw.round(g, x, y, x + fw, y + bh, r, colour | 0xFF000000);
            if (!Double.isNaN(extra) && extra > 0 && max > 0) {
                int ew = (int) Math.round(w * Math.min(1, extra / max));
                g.rect(x, y + bh - Math.max(1, bh / 3), x + ew, y + bh, extraColour | 0xFF000000);
            }
        }
    }

    /** Skill XP tracker: progress, XP per hour and time to the next level. */
    public static final class Skills extends HudModule {
        final Setting.Toggle bar, rate, eta, session;
        final Setting.Choice which;
        final Setting.Slider hideAfter, barWidth;
        final Setting.Toggle accentBar;
        final Setting.Color barColour;

        public Skills() {
            super("sb_skills", "Skill tracker", "XP per hour and time to next level for the skills you're training.", Category.SKYBLOCK, true, 1.0, 0.55);
            which = add(new Setting.Choice("which", "Show", 0, "Latest skill", "All recent skills"));
            which.in("Content");
            bar = add(new Setting.Toggle("bar", "Progress bar", true));
            bar.in("Content");
            rate = add(new Setting.Toggle("rate", "XP per hour", true));
            rate.in("Content");
            eta = add(new Setting.Toggle("eta", "Time to next level", true));
            eta.in("Content");
            session = add(new Setting.Toggle("session", "XP this session", false));
            session.in("Content");
            hideAfter = add(new Setting.Slider("hide_after", "Hide when idle for", 60, 0, 600, 10, "s"));
            hideAfter.in("Content").hint("0 keeps it on screen.");
            barWidth = add(new Setting.Slider("bar_width", "Bar width", 110, 60, 220, 2, "px"));
            barWidth.in("Look").showIf(() -> bar.value);
            accentBar = add(new Setting.Toggle("accent_bar", "Accent-coloured bar", true));
            accentBar.in("Look").showIf(() -> bar.value);
            barColour = add(new Setting.Color("bar_colour", "Bar colour", 0xFF7BD88F, false));
            barColour.in("Look").showIf(() -> bar.value && !accentBar.value);
        }

        private List<SkyBlock.SkillTrack> tracks(Client c, long now) {
            List<SkyBlock.SkillTrack> out = new ArrayList<SkyBlock.SkillTrack>();
            long idle = (long) hideAfter.value * 1000;
            if (which.is("Latest skill")) {
                SkyBlock.SkillTrack t = c.sb.lastSkill;
                if (t != null && (idle == 0 || now - t.lastGainAt < idle)) out.add(t);
            } else {
                for (SkyBlock.SkillTrack t : c.sb.skills.values()) if (idle == 0 || now - t.lastGainAt < idle) out.add(t);
            }
            return out;
        }

        @Override
        public boolean active(Client c) {
            return editing || (show(c, this) && !tracks(c, System.currentTimeMillis()).isEmpty());
        }

        private List<String[]> lines(SkyBlock.SkillTrack t, long now) {
            List<String[]> l = new ArrayList<String[]>();
            if (rate.value) l.add(new String[]{"XP/h", Format.compact(t.perHour(now))});
            if (eta.value) l.add(new String[]{"Next level", Format.duration(t.secondsToLevel(now))});
            if (session.value) l.add(new String[]{"Session", "+" + Format.compact(t.sessionXp)});
            return l;
        }

        private String title(SkyBlock.SkillTrack t) {
            if (!Double.isNaN(t.percent)) return t.name + " " + String.format(java.util.Locale.US, "%.1f%%", t.percent);
            return t.name;
        }

        private List<SkyBlock.SkillTrack> shown(Client c, long now) {
            List<SkyBlock.SkillTrack> ts = tracks(c, now);
            if (ts.isEmpty() && editing) {
                SkyBlock demo = new SkyBlock();
                demo.readActionBar("+48.5 Farming (412,330/1,000,000)", now - 120_000);
                demo.readActionBar("+52.1 Farming (412,382/1,000,000)", now);
                ts.add(demo.lastSkill);
            }
            return ts;
        }

        protected int[] measure(Gfx g, Client c, Style s) {
            long now = System.currentTimeMillis();
            int w = bar.value ? (int) barWidth.value : 0, h = 0;
            List<SkyBlock.SkillTrack> ts = shown(c, now);
            for (int i = 0; i < ts.size(); i++) {
                SkyBlock.SkillTrack t = ts.get(i);
                w = Math.max(w, g.textWidth(title(t)));
                h += g.lineHeight();
                if (bar.value) h += 5;
                for (String[] l : lines(t, now)) {
                    w = Math.max(w, g.textWidth(l[0]) + 12 + g.textWidth(l[1]));
                    h += g.lineHeight() + 1;
                }
                if (i < ts.size() - 1) h += 6;
            }
            return new int[]{w, Math.max(0, h - 1)};
        }

        protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
            long now = System.currentTimeMillis();
            int y = 0;
            List<SkyBlock.SkillTrack> ts = shown(c, now);
            for (SkyBlock.SkillTrack t : ts) {
                g.text(t.name, 0, y, s.value, s.shadow);
                if (!Double.isNaN(t.percent)) {
                    String p = String.format(java.util.Locale.US, "%.1f%%", t.percent);
                    g.text(p, w - g.textWidth(p), y, s.label, s.shadow);
                }
                y += g.lineHeight();
                if (bar.value) {
                    int col = accentBar.value ? s.accent : barColour.argb | 0xFF000000;
                    Draw.round(g, 0, y, w, y + 3, s.rounded ? 1 : 0, 0x40FFFFFF);
                    double f = Double.isNaN(t.percent) ? 0 : Math.min(1, t.percent / 100);
                    if (f > 0) Draw.round(g, 0, y, Math.max(2, (int) (w * f)), y + 3, s.rounded ? 1 : 0, col);
                    y += 5;
                }
                for (String[] l : lines(t, now)) {
                    g.text(l[0], 0, y, s.label, s.shadow);
                    g.text(l[1], w - g.textWidth(l[1]), y, s.value, s.shadow);
                    y += g.lineHeight() + 1;
                }
                y += 6;
            }
        }
    }
}
