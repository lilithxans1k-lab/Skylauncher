package dev.skylaunch.client.ui;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.util.Draw;

import java.util.List;

/**
 * Drag HUD elements around, scroll to resize, right-click for settings.
 * Edges snap to the screen, its centre lines, and other elements.
 */
public final class HudEditorScreen extends UiScreen {
    private static final int SNAP = 4;
    private HudModule dragged;
    private int offX, offY;
    private int guideX = -1, guideY = -1;
    private Theme t;

    public HudEditorScreen(Client c) {
        super(c);
    }

    @Override
    protected void draw(Gfx g) {
        t = c.appearance.menuTheme();
        g.rect(0, 0, width, height, 0x40000000);

        // Centre lines, faint, so people can line things up by eye.
        g.rect(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
        g.rect(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);

        List<HudModule> huds = c.hudModules();
        HudModule hover = null;
        for (HudModule h : huds) {
            if (!h.enabled) continue;
            h.editing = true;
            if (!h.active(c)) continue;
            h.render(g, c);
        }
        // Topmost element under the mouse.
        for (HudModule h : huds) {
            if (!h.enabled || !h.active(c)) continue;
            int x = h.screenX(width, h.scaledW()), y = h.screenY(height, h.scaledH());
            if (hovered(x, y, x + h.scaledW(), y + h.scaledH())) hover = h;
        }
        for (final HudModule h : huds) {
            if (!h.enabled || !h.active(c)) continue;
            final int x = h.screenX(width, h.scaledW()), y = h.screenY(height, h.scaledH());
            int w = Math.max(8, h.scaledW()), hh = Math.max(8, h.scaledH());
            boolean hot = h == hover || h == dragged;
            if (hot) g.rect(x, y, x + w, y + hh, Draw.withAlpha(t.accent, 0x22));
            Draw.dashed(g, x - 1, y - 1, x + w + 1, y + hh + 1, hot ? t.accent : 0x66FFFFFF);
            if (hot) {
                String tag = h.name + "  " + Math.round(h.scale.value * 100) + "%";
                int tw = g.textWidth(tag) + 8;
                int ty = y - 14 >= 0 ? y - 14 : y + hh + 3;
                int tx = Math.max(0, Math.min(width - tw, x));
                Draw.round(g, tx, ty, tx + tw, ty + 12, 2, t.accent);
                g.text(tag, tx + 4, ty + 2, t.onAccent, false);
            }
            drag("hud:" + h.id, x, y, x + w, y + hh, new Click() {
                public void click(int mx, int my, int b) {
                    if (b == 1) {
                        c.game.openScreen(new MenuScreen(c, h));
                        return;
                    }
                    dragged = h;
                    offX = mx - x;
                    offY = my - y;
                }
            }, new Drag() {
                public void drag(int mx, int my) {
                    move(h, mx - offX, my - offY);
                }
            }, new Runnable() {
                public void run() {
                    dragged = null;
                    guideX = guideY = -1;
                    c.markDirty();
                }
            });
        }

        if (guideX >= 0) g.rect(guideX, 0, guideX + 1, height, t.accent);
        if (guideY >= 0) g.rect(0, guideY, width, guideY + 1, t.accent);

        drawToolbar(g);
    }

    private void drawToolbar(Gfx g) {
        String hint = "Drag to move. Scroll to resize. Right-click for settings.";
        String done = "Done", mods = "All mods", reset = "Reset layout";
        int pad = 8, bh = 18;
        int doneW = g.textWidth(done) + 20, modsW = g.textWidth(mods) + 16, resetW = g.textWidth(reset) + 16;
        int hintW = g.textWidth(hint);
        int total = hintW + 16 + resetW + 6 + modsW + 6 + doneW + pad * 2;
        boolean compact = total > width - 16;
        if (compact) total -= hintW + 16;
        int x = (width - total) / 2, y = height - bh - pad * 2 - 6;
        Draw.round(g, x, y, x + total, y + bh + pad * 2, 4, t.bg);
        int bx = x + pad, by = y + pad;
        if (!compact) {
            g.text(hint, bx, by + 5, t.muted, false);
            bx += hintW + 16;
        }
        bx = button(g, reset, bx, by, resetW, bh, false, new Click() {
            public void click(int mx, int my, int b) {
                for (HudModule h : c.hudModules()) {
                    double s = h.scale.value;
                    h.resetToDefaults();
                    h.scale.value = s;
                }
                c.markDirty();
            }
        }) + 6;
        bx = button(g, mods, bx, by, modsW, bh, false, new Click() {
            public void click(int mx, int my, int b) {
                c.openMenu();
            }
        }) + 6;
        button(g, done, bx, by, doneW, bh, true, new Click() {
            public void click(int mx, int my, int b) {
                c.game.closeScreen();
            }
        });
    }

    private int button(Gfx g, String label, int x, int y, int w, int h, boolean primary, Click click) {
        boolean hov = hovered(x, y, x + w, y + h);
        int fill = primary ? (hov ? Draw.mix(t.accent, 0xFFFFFFFF, 0.12) : t.accent) : (hov ? t.selected : t.surface);
        Draw.round(g, x, y, x + w, y + h, 3, fill);
        if (!primary) Draw.outline(g, x, y, x + w, y + h, t.line);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, primary ? t.onAccent : t.text, false);
        hit("btn:" + label, x, y, x + w, y + h, click);
        return x + w;
    }

    private void move(HudModule h, int x, int y) {
        int w = h.scaledW(), hh = h.scaledH();
        guideX = guideY = -1;
        // Candidate x positions: screen edges and centre, other elements' edges.
        int[] sx = snap(x, w, width, true, h);
        x = sx[0];
        guideX = sx[1];
        int[] sy = snap(y, hh, height, false, h);
        y = sy[0];
        guideY = sy[1];
        int m = HudModule.MARGIN;
        x = Math.max(m, Math.min(width - w - m, x));
        y = Math.max(m, Math.min(height - hh - m, y));
        h.moveTo(x, y, width, height);
    }

    /** Returns {snapped position, guide line or -1}. */
    private int[] snap(int pos, int size, int screen, boolean horizontal, HudModule self) {
        int best = pos, guide = -1, bestDist = SNAP + 1;
        int m = HudModule.MARGIN;
        int[][] candidates = {
                {m, -2}, {screen - size - m, -2},
                {screen / 2 - size / 2, screen / 2},
        };
        for (int[] cand : candidates) {
            int d = Math.abs(pos - cand[0]);
            if (d < bestDist) {
                bestDist = d;
                best = cand[0];
                guide = cand[1] == -2 ? -1 : cand[1];
            }
        }
        for (HudModule o : c.hudModules()) {
            if (o == self || !o.enabled || !o.active(c)) continue;
            int oPos = horizontal ? o.screenX(width, o.scaledW()) : o.screenY(height, o.scaledH());
            int oSize = horizontal ? o.scaledW() : o.scaledH();
            int[][] edges = {
                    {oPos, oPos}, {oPos + oSize - size, oPos + oSize},
                    {oPos + oSize + 2, -2}, {oPos - size - 2, -2},
            };
            for (int[] e : edges) {
                int d = Math.abs(pos - e[0]);
                if (d < bestDist) {
                    bestDist = d;
                    best = e[0];
                    guide = e[1] == -2 ? -1 : e[1];
                }
            }
        }
        return new int[]{best, guide};
    }

    @Override
    public boolean mouseScrolled(int mx, int my, double amount) {
        for (HudModule h : c.hudModules()) {
            if (!h.enabled || !h.active(c)) continue;
            int x = h.screenX(width, h.scaledW()), y = h.screenY(height, h.scaledH());
            if (mx >= x && mx < x + h.scaledW() && my >= y && my < y + h.scaledH()) {
                h.scale.set(h.scale.value + (amount > 0 ? 0.05 : -0.05));
                c.markDirty();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        for (HudModule h : c.hudModules()) h.editing = false;
        super.onClose();
    }
}
