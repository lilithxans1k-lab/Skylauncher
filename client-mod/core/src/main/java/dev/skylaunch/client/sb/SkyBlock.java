package dev.skylaunch.client.sb;

import dev.skylaunch.client.util.Format;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live Hypixel SkyBlock data read from the sidebar, the tab list and the action bar.
 * Everything here is plain text parsing, so it is shared by every Minecraft version and
 * covered by unit tests.
 */
public final class SkyBlock {
    public boolean onSkyBlock;

    public double purse = Double.NaN;
    public String purseLabel = "Purse";
    public double bits = Double.NaN;
    public double motes = Double.NaN;
    public double copper = Double.NaN;
    public double bank = Double.NaN;
    public String location = "";
    public String area = "";
    public String server = "";
    public String profile = "";
    public String date = "";
    public String time = "";
    public boolean night;

    public double health = Double.NaN, maxHealth = Double.NaN;
    public double defense = Double.NaN;
    public double mana = Double.NaN, maxMana = Double.NaN;
    public double overflow = Double.NaN;
    public long lastStatsAt;

    public final Map<String, SkillTrack> skills = new LinkedHashMap<String, SkillTrack>();
    public SkillTrack lastSkill;

    private static final Pattern PURSE = Pattern.compile("^(Purse|Piggy): ([\\d,.]+)");
    private static final Pattern BITS = Pattern.compile("^Bits: ([\\d,.]+)");
    private static final Pattern MOTES = Pattern.compile("^Motes: ([\\d,.]+)");
    private static final Pattern COPPER = Pattern.compile("^Copper: ([\\d,.]+)");
    private static final Pattern DATE = Pattern.compile("^((?:Early |Late )?(?:Spring|Summer|Autumn|Winter) \\d+(?:st|nd|rd|th))$");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}:\\d{2}(?:am|pm))\\s*(.*)$");
    private static final Pattern SERVER = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}\\s+(\\S+)");

    private static final Pattern TAB_BANK = Pattern.compile("^Bank: ([\\d,.]+[kKmMbB]?)");
    private static final Pattern TAB_AREA = Pattern.compile("^Area: (.+)$");
    private static final Pattern TAB_SERVER = Pattern.compile("^Server: (\\S+)");
    private static final Pattern TAB_PROFILE = Pattern.compile("^Profile: ([A-Za-z]+)");

    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
    private static final Pattern DEFENSE = Pattern.compile("([\\d,]+)❈ Defense");
    private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)✎");
    private static final Pattern OVERFLOW = Pattern.compile("([\\d,]+)ʬ");
    private static final Pattern SKILL = Pattern.compile("\\+([\\d,.]+) ([A-Z][A-Za-z]+) \\(([^)]+)\\)");
    private static final Pattern SKILL_FRACTION = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)");
    private static final Pattern SKILL_PERCENT = Pattern.compile("([\\d.]+)%");

    /**
     * Hypixel pads sidebar lines with emoji and other filler so every line is unique.
     * Keep only ordinary text.
     */
    public static String clean(String raw) {
        String s = Format.strip(raw);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) continue;
            if (Character.getType(c) == Character.FORMAT) continue;
            b.append(c);
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    public void readScoreboard(String title, List<String> lines) {
        String t = clean(title).toUpperCase();
        onSkyBlock = t.contains("SKYBLOCK") || t.contains("SKIBLOCK");
        if (!onSkyBlock) return;
        boolean sawPurse = false, sawBits = false, sawMotes = false, sawCopper = false;
        for (String raw : lines) {
            String line = clean(raw);
            Matcher m;
            if ((m = PURSE.matcher(line)).find()) {
                purseLabel = m.group(1);
                purse = Format.parse(m.group(2));
                sawPurse = true;
            } else if ((m = BITS.matcher(line)).find()) {
                bits = Format.parse(m.group(1));
                sawBits = true;
            } else if ((m = MOTES.matcher(line)).find()) {
                motes = Format.parse(m.group(1));
                sawMotes = true;
            } else if ((m = COPPER.matcher(line)).find()) {
                copper = Format.parse(m.group(1));
                sawCopper = true;
            } else if (line.startsWith("⏣") || line.startsWith("ф")) {
                location = line.substring(1).trim();
            } else if ((m = DATE.matcher(line)).find()) {
                date = m.group(1);
            } else if ((m = TIME.matcher(line)).find()) {
                time = m.group(1);
                night = m.group(2).contains("☽");
            } else if ((m = SERVER.matcher(line)).find()) {
                server = m.group(1);
            }
        }
        if (!sawPurse) purse = Double.NaN;
        if (!sawBits) bits = Double.NaN;
        if (!sawMotes) motes = Double.NaN;
        if (!sawCopper) copper = Double.NaN;
    }

    public void readTab(List<String> lines) {
        for (String raw : lines) {
            String line = clean(raw);
            Matcher m;
            if ((m = TAB_BANK.matcher(line)).find()) bank = Format.parse(m.group(1));
            else if ((m = TAB_AREA.matcher(line)).find()) area = m.group(1).trim();
            else if ((m = TAB_SERVER.matcher(line)).find()) server = m.group(1);
            else if ((m = TAB_PROFILE.matcher(line)).find()) profile = m.group(1);
        }
    }

    /** Parse an action bar message. `now` is System.currentTimeMillis(). */
    public void readActionBar(String raw, long now) {
        String s = Format.strip(raw);
        Matcher m;
        boolean any = false;
        if ((m = HEALTH.matcher(s)).find()) {
            health = Format.parse(m.group(1));
            maxHealth = Format.parse(m.group(2));
            any = true;
        }
        if ((m = DEFENSE.matcher(s)).find()) {
            defense = Format.parse(m.group(1));
            any = true;
        }
        if ((m = MANA.matcher(s)).find()) {
            mana = Format.parse(m.group(1));
            maxMana = Format.parse(m.group(2));
            any = true;
        }
        if ((m = OVERFLOW.matcher(s)).find()) overflow = Format.parse(m.group(1));
        else if (any) overflow = Double.NaN;
        if (any) lastStatsAt = now;

        if ((m = SKILL.matcher(s)).find()) {
            double gain = Format.parse(m.group(1));
            String name = m.group(2);
            SkillTrack t = skills.get(name);
            if (t == null) {
                t = new SkillTrack(name);
                skills.put(name, t);
            }
            String inner = m.group(3);
            Matcher f = SKILL_FRACTION.matcher(inner);
            Matcher p = SKILL_PERCENT.matcher(inner);
            if (f.find()) {
                t.current = Format.parse(f.group(1));
                t.needed = Format.parse(f.group(2));
                t.percent = t.needed > 0 ? t.current / t.needed * 100 : Double.NaN;
            } else if (p.find()) {
                t.percent = Double.parseDouble(p.group(1));
                t.current = Double.NaN;
                t.needed = Double.NaN;
            }
            t.add(gain, now);
            lastSkill = t;
        }
    }

    public boolean statsFresh(long now) {
        return now - lastStatsAt < 5000;
    }

    public void reset() {
        onSkyBlock = false;
        purse = bits = motes = copper = bank = Double.NaN;
        location = area = server = profile = date = time = "";
        health = maxHealth = defense = mana = maxMana = overflow = Double.NaN;
        lastStatsAt = 0;
    }

    /** XP gains for one skill, kept for a rolling window to estimate XP per hour. */
    public static final class SkillTrack {
        public static final long WINDOW_MS = 10 * 60 * 1000;
        public final String name;
        public double current = Double.NaN, needed = Double.NaN, percent = Double.NaN;
        public double sessionXp;
        public long lastGainAt;
        private final Deque<long[]> gains = new ArrayDeque<long[]>(); // {time, xp*1000}

        SkillTrack(String name) {
            this.name = name;
        }

        void add(double xp, long now) {
            gains.addLast(new long[]{now, (long) Math.round(xp * 1000)});
            sessionXp += xp;
            lastGainAt = now;
            trim(now);
        }

        private void trim(long now) {
            while (!gains.isEmpty() && now - gains.peekFirst()[0] > WINDOW_MS) gains.removeFirst();
        }

        /** Estimated XP per hour over the last 10 minutes (or since the first gain). */
        public double perHour(long now) {
            trim(now);
            if (gains.isEmpty()) return 0;
            double sum = 0;
            for (long[] g : gains) sum += g[1] / 1000.0;
            long span = Math.max(60_000L, Math.min(WINDOW_MS, now - gains.peekFirst()[0]));
            return sum / (span / 3_600_000.0);
        }

        /** Seconds until the next level at the current rate, NaN when unknown. */
        public double secondsToLevel(long now) {
            double rate = perHour(now);
            if (Double.isNaN(current) || Double.isNaN(needed) || rate <= 0) return Double.NaN;
            return (needed - current) / rate * 3600;
        }

        public boolean idle(long now) {
            return now - lastGainAt > 60_000;
        }
    }
}
