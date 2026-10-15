package dev.skylaunch.client.features;

import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Draw;

/**
 * Visual tweaks. The settings live here; each Minecraft version applies them with its own
 * hooks (gamma, crosshair, hurt tint, block outline).
 */
public final class Visual {
    private Visual() {}

    public static final class Fullbright extends Module {
        public Fullbright() {
            super("fullbright", "Fullbright", "See clearly in caves and at night.", Category.VISUAL, false);
        }
    }

    public static final class Crosshair extends Module {
        public final Setting.Choice style;
        public final Setting.Slider size, gap, thickness;
        public final Setting.Color colour;
        public final Setting.Toggle outline, dot;

        public Crosshair() {
            super("crosshair", "Custom crosshair", "Replace the crosshair with your own shape and colour.", Category.VISUAL, false);
            style = add(new Setting.Choice("style", "Shape", 0, "Cross", "Dot", "Circle", "T"));
            size = add(new Setting.Slider("size", "Size", 5, 1, 15, 1, "px"));
            size.showIf(() -> !style.is("Dot"));
            gap = add(new Setting.Slider("gap", "Gap", 2, 0, 8, 1, "px"));
            gap.showIf(() -> style.is("Cross") || style.is("T"));
            thickness = add(new Setting.Slider("thickness", "Thickness", 1, 1, 4, 1, "px"));
            colour = add(new Setting.Color("colour", "Colour", 0xFFFFFFFF, true));
            outline = add(new Setting.Toggle("outline", "Dark outline", true));
            dot = add(new Setting.Toggle("dot", "Centre dot", false));
            dot.showIf(() -> !style.is("Dot"));
        }

        /** Draws the crosshair centred on (cx, cy). */
        public void draw(Gfx g, int cx, int cy) {
            int t = (int) thickness.value, sz = (int) size.value, gp = (int) gap.value;
            int col = colour.argb;
            int ol = Draw.withAlpha(0xFF000000, (col >>> 24) * 3 / 4);
            int h0 = t / 2;
            if (style.is("Dot")) {
                box(g, cx - h0 - 1, cy - h0 - 1, cx - h0 + t + 1, cy - h0 + t + 1, col, ol);
                return;
            }
            if (style.is("Circle")) {
                int r = sz;
                for (int a = 0; a < 360; a += 6) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * r), py = cy + (int) Math.round(Math.sin(rad) * r);
                    if (outline.value) g.rect(px - h0 - 1, py - h0 - 1, px - h0 + t + 1, py - h0 + t + 1, ol);
                }
                for (int a = 0; a < 360; a += 6) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * r), py = cy + (int) Math.round(Math.sin(rad) * r);
                    g.rect(px - h0, py - h0, px - h0 + t, py - h0 + t, col);
                }
            } else {
                // left, right, bottom, (top unless T)
                box(g, cx - gp - sz, cy - h0, cx - gp, cy - h0 + t, col, ol);
                box(g, cx + gp + 1, cy - h0, cx + gp + 1 + sz, cy - h0 + t, col, ol);
                box(g, cx - h0, cy + gp + 1, cx - h0 + t, cy + gp + 1 + sz, col, ol);
                if (!style.is("T")) box(g, cx - h0, cy - gp - sz, cx - h0 + t, cy - gp, col, ol);
            }
            if (dot.value) box(g, cx - h0, cy - h0, cx - h0 + t, cy - h0 + t, col, ol);
        }

        private void box(Gfx g, int x1, int y1, int x2, int y2, int col, int ol) {
            if (outline.value) g.rect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, ol);
            g.rect(x1, y1, x2, y2, col);
        }
    }

    public static final class HitColour extends Module {
        public final Setting.Color colour;

        public HitColour() {
            super("hit_colour", "Hit colour", "Change the red tint on mobs and players when they take damage.", Category.VISUAL, false);
            colour = add(new Setting.Color("colour", "Tint", 0x66FFFFFF, true));
        }
    }

    public static final class BlockOutline extends Module {
        public final Setting.Color fill;
        public final Setting.Color line;
        public final Setting.Slider width;
        public final Setting.Toggle keepVanilla;
        /** Set by the version layer: whether it can recolour the outline lines themselves. */
        public static boolean canStyleLines = true;

        public BlockOutline() {
            super("block_outline", "Block outline", "Highlight the block you're looking at.", Category.VISUAL, false);
            fill = add(new Setting.Color("fill", "Fill", 0x2E4CC2FF, true));
            line = add(new Setting.Color("line", "Outline colour", 0xCC4CC2FF, true));
            line.showIf(() -> canStyleLines);
            width = add(new Setting.Slider("width", "Outline width", 2, 1, 6, 0.5, "px"));
            width.showIf(() -> canStyleLines);
            keepVanilla = add(new Setting.Toggle("keep_vanilla", "Keep the vanilla outline", true));
            keepVanilla.showIf(() -> !canStyleLines);
        }
    }
}
