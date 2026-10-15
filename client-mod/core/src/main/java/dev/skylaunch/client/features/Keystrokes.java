package dev.skylaunch.client.features;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Draw;

/** W A S D, mouse buttons and space bar, lighting up as you press them. */
public final class Keystrokes extends HudModule {
    final Setting.Slider keySize;
    final Setting.Slider gap;
    final Setting.Toggle mouse;
    final Setting.Toggle cpsOnMouse;
    final Setting.Toggle space;
    final Setting.Color keyColour;
    final Setting.Color pressedColour;
    final Setting.Toggle accentPressed;
    final Setting.Slider fade;

    private final double[] glow = new double[7];
    private long last = System.currentTimeMillis();

    public Keystrokes() {
        super("keystrokes", "Keystrokes", "Shows W, A, S, D, mouse buttons and space as you press them.", Category.HUD, false, 0.0, 0.70);
        keySize = add(new Setting.Slider("key_size", "Key size", 20, 14, 32, 1, "px"));
        keySize.in("Keys");
        gap = add(new Setting.Slider("gap", "Gap", 2, 0, 6, 1, "px"));
        gap.in("Keys");
        mouse = add(new Setting.Toggle("mouse", "Mouse buttons", true));
        mouse.in("Keys");
        cpsOnMouse = add(new Setting.Toggle("cps", "CPS on mouse buttons", true));
        cpsOnMouse.in("Keys").showIf(() -> mouse.value);
        space = add(new Setting.Toggle("space", "Space bar", true));
        space.in("Keys");
        keyColour = add(new Setting.Color("key_colour", "Key colour", 0x8C101012, true));
        keyColour.in("Keys");
        accentPressed = add(new Setting.Toggle("accent_pressed", "Use accent when pressed", true));
        accentPressed.in("Keys");
        pressedColour = add(new Setting.Color("pressed_colour", "Pressed colour", 0xE6FFFFFF, true));
        pressedColour.in("Keys").showIf(() -> !accentPressed.value);
        fade = add(new Setting.Slider("fade", "Fade", 80, 0, 300, 10, "ms"));
        fade.in("Keys");
        // Keys draw their own boxes, so the outer background defaults off.
        background.setDefault("Off");
    }

    @Override
    protected int[] measure(Gfx g, Client c, Style s) {
        int k = (int) keySize.value, gp = (int) gap.value;
        int w = k * 3 + gp * 2;
        int h = k * 2 + gp;
        if (mouse.value) h += gp + k;
        if (space.value) h += gp + Math.max(8, k / 2);
        return new int[]{w, h};
    }

    @Override
    protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
        long now = System.currentTimeMillis();
        double dt = Math.max(1, now - last);
        last = now;
        boolean[] down = {
                c.game.forwardDown(), c.game.leftDown(), c.game.backDown(), c.game.rightDown(),
                c.game.attackDown(), c.game.useDown(), c.game.jumpDown()};
        double step = fade.value <= 0 ? 1 : dt / fade.value;
        for (int i = 0; i < glow.length; i++) {
            glow[i] = down[i] ? Math.min(1, glow[i] + step) : Math.max(0, glow[i] - step);
            if (editing) glow[i] = i == 0 ? 1 : 0;
        }
        int k = (int) keySize.value, gp = (int) gap.value;
        key(g, s, "W", k + gp, 0, k, k, glow[0]);
        key(g, s, "A", 0, k + gp, k, k, glow[1]);
        key(g, s, "S", k + gp, k + gp, k, k, glow[2]);
        key(g, s, "D", (k + gp) * 2, k + gp, k, k, glow[3]);
        int y = (k + gp) * 2;
        if (mouse.value) {
            int mw = (w - gp) / 2;
            String l = cpsOnMouse.value ? c.cps.left() + " CPS" : "LMB";
            String r = cpsOnMouse.value ? c.cps.right() + " CPS" : "RMB";
            key(g, s, l, 0, y, mw, k, glow[4]);
            key(g, s, r, mw + gp, y, w - mw - gp, k, glow[5]);
            y += k + gp;
        }
        if (space.value) {
            int sh = Math.max(8, k / 2);
            key(g, s, null, 0, y, w, sh, glow[6]);
            int bar = Math.max(1, sh / 6);
            int col = Draw.mix(s.value, s.bg | 0xFF000000, glow[6]);
            g.rect(w / 2 - w / 6, y + sh / 2 - bar / 2, w / 2 + w / 6, y + sh / 2 - bar / 2 + bar, col);
        }
    }

    private void key(Gfx g, Style s, String label, int x, int y, int w, int h, double t) {
        int pressed = accentPressed.value ? (s.accent & 0xFFFFFF) | 0xE6000000 : pressedColour.argb;
        int fill = Draw.mix(keyColour.argb, pressed, t);
        Draw.round(g, x, y, x + w, y + h, s.rounded ? 2 : 0, fill);
        if (label != null) {
            int textCol = Draw.mix(s.value, Draw.onColor(pressed), t);
            g.text(label, x + (w - g.textWidth(label)) / 2, y + (h - g.lineHeight()) / 2 + 1, textCol, s.shadow && t < 0.5);
        }
    }
}
