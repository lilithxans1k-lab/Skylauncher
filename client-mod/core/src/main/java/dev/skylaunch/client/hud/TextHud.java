package dev.skylaunch.client.hud;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.module.Setting;

import java.util.ArrayList;
import java.util.List;

/** HUD element made of label/value rows: FPS, coordinates, SkyBlock stats and so on. */
public abstract class TextHud extends HudModule {
    public final Setting.Toggle labels;
    public final Setting.Choice layout;
    public final Setting.Slider spacing;

    protected TextHud(String id, String name, String description, Category category, boolean on, double px, double py) {
        super(id, name, description, category, on, px, py);
        labels = add(new Setting.Toggle("labels", "Show labels", true));
        labels.in("Text");
        layout = add(new Setting.Choice("layout", "Layout", 0, "Inline", "Columns", "Right aligned"));
        layout.in("Text").hint("Columns lines values up on the right edge.");
        spacing = add(new Setting.Slider("spacing", "Line spacing", 2, 0, 8, 1, "px"));
        spacing.in("Text");
    }

    /** One row of text. A null label shows the value alone. valueColour 0 means the theme colour. */
    public static final class Row {
        public final String label, value;
        public final int valueColour;

        public Row(String label, String value) {
            this(label, value, 0);
        }

        public Row(String label, String value, int valueColour) {
            this.label = label;
            this.value = value;
            this.valueColour = valueColour;
        }
    }

    protected abstract void rows(Client c, List<Row> out);

    private final List<Row> cache = new ArrayList<Row>();

    private List<Row> currentRows(Client c) {
        cache.clear();
        rows(c, cache);
        return cache;
    }

    private String labelOf(Row r) {
        return labels.value && r.label != null && !r.label.isEmpty() ? r.label : null;
    }

    @Override
    protected int[] measure(Gfx g, Client c, Style s) {
        List<Row> rows = currentRows(c);
        if (rows.isEmpty()) return new int[]{0, 0};
        int gap = g.textWidth(" ");
        int w = 0, maxL = 0, maxV = 0;
        for (Row r : rows) {
            String l = labelOf(r);
            int lw = l == null ? 0 : g.textWidth(l);
            int vw = g.textWidth(r.value);
            maxL = Math.max(maxL, lw);
            maxV = Math.max(maxV, vw);
            w = Math.max(w, lw + (l == null ? 0 : gap) + vw);
        }
        if (layout.is("Columns")) w = Math.max(w, maxL + gap * 3 + maxV);
        int h = rows.size() * g.lineHeight() + (rows.size() - 1) * (int) spacing.value - 1;
        return new int[]{w, h};
    }

    @Override
    protected void drawContent(Gfx g, Client c, Style s, int w, int h) {
        int gap = g.textWidth(" ");
        int y = 0;
        for (Row r : cache) {
            String l = labelOf(r);
            int vc = r.valueColour != 0 ? r.valueColour : s.value;
            int vw = g.textWidth(r.value);
            if (layout.is("Inline")) {
                int x = 0;
                if (l != null) {
                    g.text(l, 0, y, s.label, s.shadow);
                    x = g.textWidth(l) + gap;
                }
                g.text(r.value, x, y, vc, s.shadow);
            } else if (layout.is("Columns")) {
                if (l != null) g.text(l, 0, y, s.label, s.shadow);
                g.text(r.value, w - vw, y, vc, s.shadow);
            } else {
                int total = vw + (l == null ? 0 : g.textWidth(l) + gap);
                int x = w - total;
                if (l != null) {
                    g.text(l, x, y, s.label, s.shadow);
                    x += g.textWidth(l) + gap;
                }
                g.text(r.value, x, y, vc, s.shadow);
            }
            y += g.lineHeight() + (int) spacing.value;
        }
    }
}
