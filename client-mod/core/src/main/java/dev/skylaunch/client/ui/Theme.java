package dev.skylaunch.client.ui;

import dev.skylaunch.client.util.Draw;

/** The launcher's palette, in game. Neutral surfaces with a single accent colour. */
public final class Theme {
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

    private Theme(boolean dark, int accent) {
        this.dark = dark;
        this.accent = accent | 0xFF000000;
        this.onAccent = Draw.onColor(this.accent);
        if (dark) {
            scrim = 0x99000000;
            bg = 0xF51F1F1F;
            sidebar = 0xF51A1A1A;
            surface = 0xFF2A2A2A;
            hover = 0x0DFFFFFF;
            selected = 0x17FFFFFF;
            line = 0xFF333333;
            text = 0xFFF0F0F0;
            muted = 0xFFA8A8A8;
            faint = 0xFF7A7A7A;
            danger = 0xFFFF99A4;
        } else {
            scrim = 0x66000000;
            bg = 0xF7F9F9F9;
            sidebar = 0xF7F0F0F0;
            surface = 0xFFFFFFFF;
            hover = 0x0A000000;
            selected = 0x12000000;
            line = 0xFFE2E2E2;
            text = 0xFF1A1A1A;
            muted = 0xFF616161;
            faint = 0xFF8A8A8A;
            danger = 0xFFC42B1C;
        }
    }

    public static Theme dark(int accent) {
        return new Theme(true, accent);
    }

    public static Theme light(int accent) {
        return new Theme(false, accent);
    }
}
