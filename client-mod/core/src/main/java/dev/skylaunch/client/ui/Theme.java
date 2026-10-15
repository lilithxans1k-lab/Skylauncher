package dev.skylaunch.client.ui;

import dev.skylaunch.client.util.Draw;

/**
 * The launcher's palettes, in game. Each theme has its own surfaces and accent, and they match
 * the launcher's themes of the same name (src/renderer/styles.css).
 */
public final class Theme {
    public static final String[] NAMES = {"Midnight", "Dawn", "Violet", "Light"};

    public final String name;
    public final boolean dark;
    public final int scrim;      // dims the game behind the menu
    public final int bg;         // main panel
    public final int sidebar;
    public final int surface;    // inputs, cards
    public final int hover;
    public final int selected;
    public final int line;
    public final int text;
    public final int muted;
    public final int faint;
    public final int accent;
    public final int onAccent;
    public final int danger;

    private Theme(String name, boolean dark, int[] p, int accent) {
        this.name = name;
        this.dark = dark;
        this.accent = accent | 0xFF000000;
        this.onAccent = Draw.onColor(this.accent);
        scrim = p[0];
        bg = p[1];
        sidebar = p[2];
        surface = p[3];
        hover = p[4];
        selected = p[5];
        line = p[6];
        text = p[7];
        muted = p[8];
        faint = p[9];
        danger = p[10];
    }

    /** The theme's own accent colour. */
    public static int defaultAccent(String name) {
        if ("Dawn".equals(name)) return 0xFFFF9F2E;
        if ("Violet".equals(name)) return 0xFFA87CFF;
        if ("Light".equals(name)) return 0xFF0067C0;
        return 0xFF4CC2FF;
    }

    /** A theme by name; unknown names get Midnight. `accent` 0 means the theme's own accent. */
    public static Theme named(String name, int accent) {
        if (!"Dawn".equals(name) && !"Violet".equals(name) && !"Light".equals(name)) name = "Midnight";
        int a = accent == 0 ? defaultAccent(name) : accent;
        if ("Dawn".equals(name)) return new Theme(name, true, new int[] {
                0x99050200, 0xF51D1712, 0xF5191410, 0xFF29211A, 0x0DFFF3E6, 0x17FFF3E6,
                0xFF382D24, 0xFFF7EFE6, 0xFFBFAE9C, 0xFF8A7A6A, 0xFFFF8A80}, a);
        if ("Violet".equals(name)) return new Theme(name, true, new int[] {
                0x99040208, 0xF517131F, 0xF513101B, 0xFF221C2D, 0x0DF5EEFF, 0x17F5EEFF,
                0xFF30283D, 0xFFF2EEFA, 0xFFB2A8C6, 0xFF7D7392, 0xFFFF99B4}, a);
        if ("Light".equals(name)) return new Theme(name, false, new int[] {
                0x66000814, 0xF7F9FAFC, 0xF7EFF2F7, 0xFFFFFFFF, 0x0A00143C, 0x1200143C,
                0xFFDFE4EC, 0xFF161A22, 0xFF5A6170, 0xFF8890A0, 0xFFC42B1C}, a);
        return new Theme("Midnight", true, new int[] {
                0x99000003, 0xF516181D, 0xF5121418, 0xFF20232A, 0x0DFFFFFF, 0x17FFFFFF,
                0xFF2C3038, 0xFFEEF0F4, 0xFFA3A8B3, 0xFF737985, 0xFFFF99A4}, a);
    }

    public static Theme dark(int accent) {
        return named("Midnight", accent);
    }

    public static Theme light(int accent) {
        return named("Light", accent);
    }
}
