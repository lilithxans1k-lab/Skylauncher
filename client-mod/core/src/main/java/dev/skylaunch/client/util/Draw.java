package dev.skylaunch.client.util;

import dev.skylaunch.client.Gfx;

/** Drawing helpers built only from rectangles, so they look the same on every version. */
public final class Draw {
    private Draw() {}

    /** Filled rectangle with corners rounded by `radius` pixels (0 = square). */
    public static void round(Gfx g, int x1, int y1, int x2, int y2, int radius, int argb) {
        if ((argb >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
        int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            g.rect(x1, y1, x2, y2, argb);
            return;
        }
        g.rect(x1, y1 + r, x2, y2 - r, argb);
        for (int k = 0; k < r; k++) {
            double dy = r - k - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.rect(x1 + inset, y1 + k, x2 - inset, y1 + k + 1, argb);
            g.rect(x1 + inset, y2 - k - 1, x2 - inset, y2 - k, argb);
        }
    }

    /** 1px border inside the rectangle. */
    public static void outline(Gfx g, int x1, int y1, int x2, int y2, int argb) {
        g.rect(x1, y1, x2, y1 + 1, argb);
        g.rect(x1, y2 - 1, x2, y2, argb);
        g.rect(x1, y1 + 1, x1 + 1, y2 - 1, argb);
        g.rect(x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    /** Dashed 1px border, used by the HUD editor. */
    public static void dashed(Gfx g, int x1, int y1, int x2, int y2, int argb) {
        for (int x = x1; x < x2; x += 4) {
            g.rect(x, y1, Math.min(x + 2, x2), y1 + 1, argb);
            g.rect(x, y2 - 1, Math.min(x + 2, x2), y2, argb);
        }
        for (int y = y1; y < y2; y += 4) {
            g.rect(x1, y, x1 + 1, Math.min(y + 2, y2), argb);
            g.rect(x2 - 1, y, x2, Math.min(y + 2, y2), argb);
        }
    }

    public static int alpha(int argb, double a) {
        int base = argb >>> 24;
        int na = (int) Math.round(base * Math.max(0, Math.min(1, a)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Readable text colour (near-black or white) on top of `argb`. */
    public static int onColor(int argb) {
        double r = ((argb >> 16) & 255) / 255.0, g = ((argb >> 8) & 255) / 255.0, b = (argb & 255) / 255.0;
        double lum = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
        return lum > 0.25 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static double lin(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Shortens text with "..." so it fits in `width`. */
    public static String fit(Gfx g, String s, int width) {
        if (g.textWidth(s) <= width) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && g.textWidth(s.substring(0, end) + dots) > width) end--;
        return s.substring(0, end) + dots;
    }

    public static void centered(Gfx g, String s, int cx, int y, int argb, boolean shadow) {
        g.text(s, cx - g.textWidth(s) / 2, y, argb, shadow);
    }
}
