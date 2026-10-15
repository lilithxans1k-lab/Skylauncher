package dev.skylaunch.client.hud;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.module.Appearance;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Draw;

import java.util.Map;

/**
 * A module that draws something on the HUD. It can be dragged and resized in the HUD editor.
 * Position is stored as a fraction of the free space, so layouts survive resolution and
 * GUI-scale changes.
 */
public abstract class HudModule extends Module {
    /** 0..1: left edge .. right edge / top edge .. bottom edge. */
    public double px, py;
    private final double defPx, defPy;

    public final Setting.Slider scale;
    public final Setting.Choice background;
    public final Setting.Choice shadow;
    public final Setting.Toggle customColours;
    public final Setting.Color valueColour;
    public final Setting.Color labelColour;
    public final Setting.Color bgColour;

    /** Size from the last layout, in unscaled HUD pixels. */
    public int lastW, lastH;

    protected HudModule(String id, String name, String description, Category category, boolean on, double px, double py) {
        super(id, name, description, category, on);
        this.px = this.defPx = px;
        this.py = this.defPy = py;
        scale = add(new Setting.Slider("scale", "Size", 1.0, 0.5, 2.5, 0.05, "x"));
        scale.in("Look");
        background = add(new Setting.Choice("background", "Background", 0, "Default", "On", "Off"));
        background.in("Look");
        shadow = add(new Setting.Choice("shadow", "Text shadow", 0, "Default", "On", "Off"));
        shadow.in("Look");
        customColours = add(new Setting.Toggle("custom_colours", "Own colours", false));
        customColours.in("Look").hint("Override the Appearance colours for this element only.");
        valueColour = add(new Setting.Color("value_colour", "Value colour", 0xFFFFFFFF, true));
        valueColour.in("Look").showIf(() -> customColours.value);
        labelColour = add(new Setting.Color("label_colour", "Label colour", 0xFFB4B4B4, true));
        labelColour.in("Look").showIf(() -> customColours.value);
        bgColour = add(new Setting.Color("bg_colour", "Background colour", 0x8C101012, true));
        bgColour.in("Look").showIf(() -> customColours.value);
    }

    /** True when there is something to show right now (e.g. SkyBlock widgets off SkyBlock). */
    public boolean active(Client c) {
        return true;
    }

    /** Size of the content (without padding) for the current data. */
    protected abstract int[] measure(Gfx g, Client c, Style s);

    /** Draw the content with its top-left at (0, 0) of the padded box. */
    protected abstract void drawContent(Gfx g, Client c, Style s, int w, int h);

    /** Shown in the HUD editor when there's no live data yet, so the box isn't empty. */
    public boolean editing;

    public final void render(Gfx g, Client c) {
        Style s = style(c);
        int[] size = measure(g, c, s);
        int pad = s.background ? s.padding : 0;
        int w = size[0] + pad * 2;
        int h = size[1] + pad * 2;
        lastW = w;
        lastH = h;
        if (w <= 0 || h <= 0) return;
        double sc = scale.value;
        int sw = (int) Math.ceil(w * sc), sh = (int) Math.ceil(h * sc);
        int x = screenX(g.width(), sw), y = screenY(g.height(), sh);
        g.push(x, y, (float) sc);
        if (s.background) Draw.round(g, 0, 0, w, h, s.rounded ? 3 : 0, s.bg);
        g.push(pad, pad, 1f);
        drawContent(g, c, s, size[0], size[1]);
        g.pop();
        g.pop();
    }

    public int scaledW() {
        return (int) Math.ceil(lastW * scale.value);
    }

    public int scaledH() {
        return (int) Math.ceil(lastH * scale.value);
    }

    /** Gap kept between HUD elements and the screen edge. */
    public static final int MARGIN = 3;

    public int screenX(int screenW, int scaledW) {
        return MARGIN + (int) Math.round(px * Math.max(0, screenW - scaledW - MARGIN * 2));
    }

    public int screenY(int screenH, int scaledH) {
        return MARGIN + (int) Math.round(py * Math.max(0, screenH - scaledH - MARGIN * 2));
    }

    public void moveTo(int x, int y, int screenW, int screenH) {
        int fw = Math.max(1, screenW - scaledW() - MARGIN * 2), fh = Math.max(1, screenH - scaledH() - MARGIN * 2);
        px = Math.max(0, Math.min(1, (x - MARGIN) / (double) fw));
        py = Math.max(0, Math.min(1, (y - MARGIN) / (double) fh));
    }

    public Style style(Client c) {
        Appearance a = c.appearance;
        Style s = new Style();
        s.background = background.is("Default") ? a.background.is("Box") : background.is("On");
        s.shadow = shadow.is("Default") ? a.shadow.value : shadow.is("On");
        s.value = customColours.value ? valueColour.argb : a.text.argb;
        s.label = customColours.value ? labelColour.argb : a.label.argb;
        s.bg = customColours.value ? bgColour.argb : a.backgroundColor.argb;
        s.rounded = a.rounded.value;
        s.padding = (int) a.padding.value;
        s.accent = a.accent.argb | 0xFF000000;
        return s;
    }

    @Override
    public void resetToDefaults() {
        super.resetToDefaults();
        px = defPx;
        py = defPy;
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("x", px);
        m.put("y", py);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        if (m.get("x") instanceof Number) px = ((Number) m.get("x")).doubleValue();
        if (m.get("y") instanceof Number) py = ((Number) m.get("y")).doubleValue();
    }

    public static final class Style {
        public boolean background, shadow, rounded;
        public int value, label, bg, accent, padding;
    }
}
