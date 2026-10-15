package dev.skylaunch.client.util;

import java.util.Locale;

public final class Format {
    private Format() {}

    /** 1234567 -> "1,234,567". */
    public static String full(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.format(Locale.US, "%,d", (long) Math.rint(v));
        return String.format(Locale.US, "%,.1f", v);
    }

    /** 1234567 -> "1.23M", 15300 -> "15.3k", 950 -> "950". */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a < 1000) return full(v);
        String[] units = {"k", "M", "B", "T"};
        int u = -1;
        while (a >= 1000 && u < units.length - 1) {
            a /= 1000;
            u++;
        }
        String n = a >= 100 ? String.format(Locale.US, "%.0f", a) : a >= 10 ? String.format(Locale.US, "%.1f", a) : String.format(Locale.US, "%.2f", a);
        if (n.contains(".")) n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
        return (v < 0 ? "-" : "") + n + units[u];
    }

    public static String number(double v, boolean compact) {
        return compact ? compact(v) : full(v);
    }

    /** "1,234.5", "12.3k", "1.5M" -> number. Returns NaN when unparseable. */
    public static double parse(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim().replace(",", "");
        if (t.isEmpty()) return Double.NaN;
        double mult = 1;
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        if (last == 'k') mult = 1e3;
        else if (last == 'm') mult = 1e6;
        else if (last == 'b') mult = 1e9;
        if (mult != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mult;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Seconds -> "1h 05m", "4m 12s", "38s". */
    public static String duration(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) return "--";
        long s = (long) seconds;
        if (s >= 3600) return String.format(Locale.US, "%dh %02dm", s / 3600, (s % 3600) / 60);
        if (s >= 60) return String.format(Locale.US, "%dm %02ds", s / 60, s % 60);
        return s + "s";
    }

    /** Ticks -> "1:05", "12:00", "**:**" for infinite. */
    public static String ticks(int ticks) {
        if (ticks < 0 || ticks >= 20 * 60 * 600) return "∞";
        int s = ticks / 20;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public static String roman(int n) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n - 1] : String.valueOf(n);
    }

    /** Removes Minecraft formatting codes (the section sign plus one character). */
    public static String strip(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }
}
