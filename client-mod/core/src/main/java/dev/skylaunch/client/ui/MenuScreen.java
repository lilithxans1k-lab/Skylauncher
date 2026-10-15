package dev.skylaunch.client.ui;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;
import dev.skylaunch.client.module.Appearance;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.util.Draw;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game menu (Right Shift). Same layout as the launcher: categories on the left, a list
 * of mods with switches on the right, and a settings page for each mod.
 */
public final class MenuScreen extends UiScreen {
    private static final int SIDEBAR = 120, TILE_H = 52, TILE_GAP = 6, SET_ROW = 22, HEADER = 40;

    private Module.Category category = null; // null = all mods
    private Module open = null;
    private double scroll = 0, scrollTarget = 0;
    private int contentHeight = 0;
    private Setting.Color picking = null;
    private Setting.KeyBind listening = null;
    private Theme t;

    // Small eased animations for hovers and switches, keyed by control id.
    private final java.util.Map<String, double[]> anim = new java.util.HashMap<String, double[]>();
    private long lastFrame = System.currentTimeMillis();
    private double frameDt = 16;

    /** Moves the value for `key` toward `target` (0..1) and returns it. */
    private double ease(String key, boolean on, double perSecond) {
        double[] v = anim.get(key);
        if (v == null) {
            v = new double[]{on ? 1 : 0};
            anim.put(key, v);
        }
        double step = perSecond * frameDt / 1000.0;
        double target = on ? 1 : 0;
        v[0] = v[0] < target ? Math.min(target, v[0] + step) : Math.max(target, v[0] - step);
        return v[0];
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    private final TextField search = new TextField(24) {
        @Override
        public void changed() {
            open = null;
            scrollTarget = scroll = 0;
        }
    };
    private final TextField hex = new TextField(8) {
        @Override
        public void submit() {
            if (picking != null) {
                picking.load("#" + text);
                c.markDirty();
            }
        }
    };

    public MenuScreen(Client c) {
        super(c);
    }

    /** Opens straight onto one module's settings (used by the HUD editor's right-click). */
    public MenuScreen(Client c, Module module) {
        super(c);
        this.open = module;
    }

    // ---------------------------------------------------------------- layout

    private int px, py, pw, ph;

    @Override
    protected void draw(Gfx g) {
        t = c.appearance.menuTheme();
        long now = System.currentTimeMillis();
        frameDt = Math.max(1, Math.min(100, now - lastFrame));
        lastFrame = now;
        pw = Math.min(600, width - 24);
        ph = Math.min(350, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        g.rect(0, 0, width, height, t.scrim);

        // A short scale-in when the menu opens.
        double age = Math.min(1, (System.currentTimeMillis() - openedAt) / 140.0);
        float s = (float) (0.97 + 0.03 * (1 - Math.pow(1 - age, 3)));
        float ox = px + pw / 2f - (pw / 2f) * s, oy = py + ph / 2f - (ph / 2f) * s;
        boolean anim = age < 1;
        if (anim) g.push(ox - px * s, oy - py * s, s);

        // Soft shadow: a few widening, fading rounded layers under the panel.
        for (int i = 4; i >= 1; i--) {
            int spread = i * 2;
            Draw.round(g, px - spread, py - spread + 3, px + pw + spread, py + ph + spread + 3, 6 + spread, Draw.withAlpha(0xFF000000, 18 - i * 3));
        }
        Draw.round(g, px, py, px + pw, py + ph, 6, t.bg);
        Draw.round(g, px, py, px + SIDEBAR, py + ph, 6, t.sidebar);
        g.rect(px + SIDEBAR - 6, py, px + SIDEBAR, py + ph, t.sidebar);
        g.rect(px + SIDEBAR, py, px + SIDEBAR + 1, py + ph, t.line);

        drawSidebar(g);
        if (open != null) drawSettings(g);
        else drawList(g);

        if (anim) g.pop();
    }

    private void drawSidebar(Gfx g) {
        int x = px + 8, y = py + 12;
        // Brand mark: a small accent tile next to the name.
        Draw.round(g, x + 4, y - 1, x + 13, y + 8, 2, t.accent);
        g.rect(x + 6, y + 1, x + 9, y + 4, Draw.withAlpha(t.onAccent, 0x70));
        g.text(Client.NAME, x + 18, y, t.text, false);
        y += 24;
        y = categoryItem(g, null, "All mods", x, y);
        for (Module.Category cat : Module.Category.values()) {
            if (cat == Module.Category.SETTINGS) continue;
            y = categoryItem(g, cat, cat.label, x, y);
        }
        int by = py + ph - 54;
        categoryItem(g, Module.Category.SETTINGS, "Appearance", x, by);
        g.text(c.game.platformName(), x + 4, py + ph - 16, t.faint, false);
    }

    private int categoryItem(Gfx g, final Module.Category cat, String label, int x, int y) {
        int w = SIDEBAR - 16, h = 20;
        boolean sel = open == null ? (category == cat && search.text.isEmpty())
                : (cat == Module.Category.SETTINGS ? open == c.appearance : category == cat && open != c.appearance);
        boolean hov = hovered(x, y, x + w, y + h);
        double hv = smooth(ease("cat:" + label, hov || sel, 8));
        if (hv > 0) Draw.round(g, x, y, x + w, y + h, 4, sel ? t.selected : Draw.alpha(t.hover, hv));
        if (sel) Draw.round(g, x + 1, y + 6, x + 3, y + h - 6, 1, t.accent);
        Icons.draw(g, Icons.forCategory(cat), x + 8, y + 6, sel ? t.accent : Draw.mix(t.faint, t.text, hv * 0.6));
        g.text(label, x + 22, y + 6, sel ? t.text : Draw.mix(t.muted, t.text, hv), false);
        hit("cat:" + label, x, y, x + w, y + h, new Click() {
            public void click(int mx, int my, int b) {
                search.text = "";
                scroll = scrollTarget = 0;
                picking = null;
                listening = null;
                if (cat == Module.Category.SETTINGS) {
                    open = c.appearance;
                } else {
                    category = cat;
                    open = null;
                }
            }
        });
        return y + h + 1;
    }

    private int cx() {
        return px + SIDEBAR + 1;
    }

    private int cw() {
        return pw - SIDEBAR - 1;
    }

    // ---------------------------------------------------------------- mod list

    private List<Module> visibleModules() {
        List<Module> l = new ArrayList<Module>();
        String q = search.text.trim().toLowerCase();
        for (Module m : c.modules) {
            if (m.category == Module.Category.SETTINGS) continue;
            if (!q.isEmpty()) {
                if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)) l.add(m);
            } else if (category == null || m.category == category) {
                l.add(m);
            }
        }
        return l;
    }

    private void drawList(Gfx g) {
        int x = cx() + 16, w = cw() - 32, top = py + HEADER;
        String title = !search.text.isEmpty() ? "Search" : category == null ? "All mods" : category.label;
        g.text(title, x, py + 15, t.text, false);
        drawHeaderControls(g);
        g.rect(cx(), top, cx() + cw(), top + 1, t.line);

        List<Module> mods = visibleModules();
        int viewTop = top + 1, viewBottom = py + ph - 4;
        int cols = w >= 380 ? 2 : 1;
        int tileW = (w - TILE_GAP * (cols - 1)) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        contentHeight = rows * (TILE_H + TILE_GAP) + 16;
        scroll += (scrollTarget - scroll) * 0.5;
        g.clip(cx(), viewTop, cx() + cw(), viewBottom);
        int y0 = viewTop + 10 - (int) Math.round(scroll);
        if (mods.isEmpty()) {
            Draw.centered(g, "No mods match \"" + search.text + "\"", cx() + cw() / 2, viewTop + 40, t.muted, false);
        }
        for (int i = 0; i < mods.size(); i++) {
            int tx = x + (i % cols) * (tileW + TILE_GAP), ty = y0 + (i / cols) * (TILE_H + TILE_GAP);
            if (ty + TILE_H > viewTop && ty < viewBottom) drawTile(g, mods.get(i), tx, ty, tileW, viewTop, viewBottom);
        }
        g.unclip();
        drawScrollbar(g, viewTop, viewBottom);
    }

    /** One mod as a tile: name, a two-line description, and its switch. Click to open settings. */
    private void drawTile(Gfx g, final Module m, int x, int y, int w, int clipTop, int clipBottom) {
        int h = TILE_H;
        int hy1 = Math.max(y, clipTop), hy2 = Math.min(y + h, clipBottom);
        boolean hov = hy2 > hy1 && hovered(x, hy1, x + w, hy2);
        double hv = smooth(ease("tile:" + m.id, hov, 7));
        double on = smooth(ease("on:" + m.id, m.enabled, 6));
        int base = Draw.mix(t.surface, Draw.mix(t.surface, t.text, 0.06), hv);
        Draw.round(g, x, y, x + w, y + h, 5, base);
        Draw.outline(g, x, y, x + w, y + h, Draw.mix(t.line, t.accent, on * 0.3));
        // Enabled mods get a short accent bar on the left edge.
        if (on > 0.01) Draw.round(g, x, y + 10, x + 2, y + h - 10, 1, Draw.alpha(t.accent, on));
        g.text(Draw.fit(g, m.name, w - 46), x + 10, y + 9, t.text, false);
        String[] lines = twoLines(g, m.description, w - 20);
        g.text(lines[0], x + 10, y + 22, t.muted, false);
        if (lines[1] != null) g.text(lines[1], x + 10, y + 33, t.muted, false);
        int sx = x + w - 30, sy = y + 8;
        if (m.toggleable) drawSwitch(g, sx, sy, m.enabled, "tile:" + m.id);
        if (hy2 > hy1) {
            hit("row:" + m.id, x, hy1, x + w, hy2, new Click() {
                public void click(int mx, int my, int b) {
                    openPage(m);
                }
            });
            if (m.toggleable) hit("sw:" + m.id, sx - 6, Math.max(y, clipTop), x + w, Math.min(y + 26, clipBottom), new Click() {
                public void click(int mx, int my, int b) {
                    m.setEnabled(!m.enabled);
                    c.markDirty();
                }
            });
        }
    }

    private String[] twoLines(Gfx g, String text, int w) {
        String[] out = new String[2];
        StringBuilder a = new StringBuilder();
        String[] words = text.split(" ");
        int i = 0;
        for (; i < words.length; i++) {
            String trial = a.length() == 0 ? words[i] : a + " " + words[i];
            if (g.textWidth(trial) > w && a.length() > 0) break;
            a = new StringBuilder(trial);
        }
        out[0] = Draw.fit(g, a.toString(), w);
        if (i < words.length) {
            StringBuilder b = new StringBuilder();
            for (int k = i; k < words.length; k++) b.append(k == i ? "" : " ").append(words[k]);
            out[1] = Draw.fit(g, b.toString(), w);
        }
        return out;
    }

    private void drawModuleRow(Gfx g, final Module m, int x, int y, int w, int clipTop, int clipBottom) {
        boolean hov = hovered(x, Math.max(y, clipTop), x + w, Math.min(y + TILE_H - 2, clipBottom)) && open == null;
        if (hov) Draw.round(g, x, y, x + w, y + TILE_H - 2, 3, t.hover);
        g.text(m.name, x + 8, y + 5, t.text, false);
        g.text(Draw.fit(g, m.description, w - 60), x + 8, y + 16, t.muted, false);
        int sx = x + w - 30, sy = y + 9;
        if (m.toggleable) {
            drawSwitch(g, sx, sy, m.enabled, "row:" + m.id);
            int hy1 = Math.max(y, clipTop), hy2 = Math.min(y + TILE_H - 2, clipBottom);
            if (hy2 > hy1) {
                hit("row:" + m.id, x, hy1, x + w, hy2, new Click() {
                    public void click(int mx, int my, int b) {
                        openPage(m);
                    }
                });
                hit("sw:" + m.id, sx - 6, hy1, x + w, hy2, new Click() {
                    public void click(int mx, int my, int b) {
                        m.setEnabled(!m.enabled);
                        c.markDirty();
                    }
                });
            }
        }
    }

    private void openPage(Module m) {
        open = m;
        scroll = scrollTarget = 0;
        picking = null;
        listening = null;
    }

    private void drawHeaderControls(Gfx g) {
        int right = cx() + cw() - 16;
        // Edit HUD button
        String label = "Edit HUD";
        int bw = g.textWidth(label) + 28, bh = 18, bx = right - bw, by = py + 11;
        boolean hov = hovered(bx, by, bx + bw, by + bh);
        double ehv = smooth(ease("edit", hov, 8));
        Draw.round(g, bx, by, bx + bw, by + bh, 4, Draw.mix(t.accent, 0xFFFFFFFF, 0.14 * ehv));
        Icons.draw(g, "edit", bx + 7, by + 5, t.onAccent);
        g.text(label, bx + 20, by + 5, t.onAccent, false);
        hit("edit", bx, by, bx + bw, by + bh, new Click() {
            public void click(int mx, int my, int b) {
                c.openHudEditor();
            }
        });
        // Share world (singleplayer only)
        int leftEdge = bx;
        if (c.game.inSingleplayer()) {
            String sl = c.shareAddress != null ? "Shared" : "Share world";
            int sbw = g.textWidth(sl) + 28, sbx = bx - 6 - sbw;
            boolean shov = hovered(sbx, by, sbx + sbw, by + bh);
            double shv = smooth(ease("share", shov, 8));
            Draw.round(g, sbx, by, sbx + sbw, by + bh, 4, Draw.mix(t.surface, t.selected | 0xFF000000, shv * 0.5));
            Draw.outline(g, sbx, by, sbx + sbw, by + bh, t.line);
            int scol = c.shareAddress != null ? t.accent : t.text;
            Icons.draw(g, "share", sbx + 7, by + 5, scol);
            g.text(sl, sbx + 20, by + 5, scol, false);
            hit("share", sbx, by, sbx + sbw, by + bh, new Click() {
                public void click(int mx, int my, int b) {
                    c.openShare();
                }
            });
            leftEdge = sbx;
        }
        // Search field
        if (open == null) {
            int sw = Math.min(130, cw() / 3), sx = leftEdge - 6 - sw;
            textField(g, search, "Search mods", sx, by, sw, bh);
        }
    }

    private void textField(Gfx g, TextField f, String placeholder, int x, int y, int w, int h) {
        final TextField field = f;
        boolean focus = focused == f;
        Draw.round(g, x, y, x + w, y + h, 3, t.surface);
        Draw.outline(g, x, y, x + w, y + h, t.line);
        g.rect(x + 1, y + h - (focus ? 2 : 1), x + w - 1, y + h, focus ? t.accent : t.faint);
        String shown = f.text.isEmpty() && !focus ? placeholder : f.text;
        int col = f.text.isEmpty() && !focus ? t.faint : t.text;
        String fitted = shown;
        while (g.textWidth(fitted) > w - 10 && fitted.length() > 1) fitted = fitted.substring(1);
        g.text(fitted, x + 5, y + (h - 8) / 2, col, false);
        if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cxp = x + 5 + g.textWidth(fitted);
            g.rect(cxp, y + 3, cxp + 1, y + h - 3, t.text);
        }
        hit("field", x, y, x + w, y + h, new Click() {
            public void click(int mx, int my, int b) {
                focused = field;
            }
        });
    }

    /** A Windows-style switch whose knob slides and whose colour fades when it changes. */
    private void drawSwitch(Gfx g, int x, int y, boolean on, String key) {
        int w = 20, h = 10;
        double v = smooth(ease("switch:" + key, on, 7));
        int track = Draw.mix(t.muted, t.accent, v);
        Draw.round(g, x, y, x + w, y + h, 5, track);
        if (v < 0.999) Draw.round(g, x + 1, y + 1, x + w - 1, y + h - 1, 4, Draw.alpha(t.bg | 0xFF000000, 1 - v));
        int kx = x + 2 + (int) Math.round(v * 10);
        Draw.round(g, kx, y + 2, kx + 6, y + 8, 3, Draw.mix(t.muted, t.onAccent, v));
    }

    private void drawScrollbar(Gfx g, int top, int bottom) {
        int view = bottom - top;
        int max = Math.max(0, contentHeight - view);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        if (max <= 0) return;
        int x = cx() + cw() - 5;
        int barH = Math.max(16, view * view / contentHeight);
        int barY = top + (int) ((view - barH) * (scroll / max));
        Draw.round(g, x, barY, x + 2, barY + barH, 1, t.faint);
    }

    // ---------------------------------------------------------------- settings page

    private void drawSettings(Gfx g) {
        final Module m = open;
        int x = cx() + 16, w = cw() - 32, top = py + HEADER;
        // Back link
        boolean fromAppearance = m == c.appearance;
        if (!fromAppearance) {
            String back = "< Back";
            int bw = g.textWidth(back);
            boolean hov = hovered(x - 2, py + 12, x + bw + 2, py + 26);
            g.text(back, x, py + 15, hov ? t.text : t.muted, false);
            hit("back", x - 2, py + 12, x + bw + 2, py + 26, new Click() {
                public void click(int mx, int my, int b) {
                    open = null;
                    picking = null;
                    listening = null;
                    scroll = scrollTarget = 0;
                }
            });
            g.text(m.name, x + bw + 10, py + 15, t.text, false);
        } else {
            g.text(m.name, x, py + 15, t.text, false);
        }
        drawHeaderControls(g);
        g.rect(cx(), top, cx() + cw(), top + 1, t.line);

        int viewTop = top + 1, viewBottom = py + ph - 4;
        scroll += (scrollTarget - scroll) * 0.5;
        g.clip(cx(), viewTop, cx() + cw(), viewBottom);
        int y = viewTop + 10 - (int) Math.round(scroll);
        int startY = y;

        // Description + main switch
        g.text(Draw.fit(g, m.description, w - (m.toggleable ? 40 : 0)), x, y, t.muted, false);
        if (m.toggleable) {
            int sx = x + w - 20;
            drawSwitch(g, sx, y - 1, m.enabled, "main:" + m.id);
            if (y > viewTop - 10 && y < viewBottom) hit("main", sx - 4, Math.max(y - 4, viewTop), x + w, Math.min(y + 12, viewBottom), new Click() {
                public void click(int mx, int my, int b) {
                    m.setEnabled(!m.enabled);
                    c.markDirty();
                }
            });
        }
        y += 18;

        // Mod-specific options first, the shared "Look" options last.
        List<Setting> ordered = new ArrayList<Setting>();
        for (Setting s : m.settings) if (!"Look".equals(s.section)) ordered.add(s);
        for (Setting s : m.settings) if ("Look".equals(s.section)) ordered.add(s);
        String section = "\u0000";
        for (Setting s : ordered) {
            if (!s.isVisible()) continue;
            String sec = s.section == null ? "" : s.section;
            if (!sec.equals(section)) {
                section = sec;
                if (!sec.isEmpty()) {
                    y += 6;
                    g.text(sec, x, y, t.muted, false);
                    y += 12;
                    g.rect(x, y - 2, x + w, y - 1, t.line);
                }
            }
            y = drawSetting(g, s, x, y, w, viewTop, viewBottom);
        }

        // Reset link
        y += 10;
        final String reset = "Reset " + m.name + " to defaults";
        int rw = g.textWidth(reset);
        boolean hov = hovered(x, y - 2, x + rw, y + 10);
        g.text(reset, x, y, hov ? t.danger : t.faint, false);
        if (y > viewTop && y < viewBottom) hit("reset", x, y - 2, x + rw, y + 10, new Click() {
            public void click(int mx, int my, int b) {
                m.resetToDefaults();
                picking = null;
                c.markDirty();
            }
        });
        y += 16;
        g.unclip();
        contentHeight = y - startY + 10;
        drawScrollbar(g, viewTop, viewBottom);
    }

    private boolean visibleRow(int y1, int y2, int top, int bottom) {
        return y2 > top && y1 < bottom;
    }

    private int drawSetting(Gfx g, final Setting s, int x, int y, int w, int top, int bottom) {
        int rowH = SET_ROW;
        int mid = y + rowH / 2;
        g.text(s.name, x, mid - 4, t.text, false);
        int right = x + w;
        boolean vis = visibleRow(y, y + rowH, top, bottom);

        if (s instanceof Setting.Toggle) {
            final Setting.Toggle tg = (Setting.Toggle) s;
            drawSwitch(g, right - 20, mid - 5, tg.value, "set:" + (open != null ? open.id : "") + ":" + s.id);
            if (vis) hit("t:" + s.id, x, Math.max(y, top), right, Math.min(y + rowH, bottom), new Click() {
                public void click(int mx, int my, int b) {
                    tg.value = !tg.value;
                    c.markDirty();
                }
            });
        } else if (s instanceof Setting.Slider) {
            final Setting.Slider sl = (Setting.Slider) s;
            final int tw = Math.min(110, w / 2 - 10);
            final int tx = right - tw;
            String val = sl.display();
            g.text(val, tx - 8 - g.textWidth(val), mid - 4, t.muted, false);
            g.rect(tx, mid - 1, right, mid + 1, t.line);
            double f = (sl.value - sl.min) / (sl.max - sl.min);
            int kx = tx + (int) Math.round(f * tw);
            g.rect(tx, mid - 1, kx, mid + 1, t.accent);
            boolean active = isDragging("sl:" + s.id) || hovered(tx - 4, y, right + 4, y + rowH);
            int kr = active ? 4 : 3;
            Draw.round(g, kx - kr, mid - kr, kx + kr, mid + kr, kr, t.accent);
            if (vis) {
                final Drag d = new Drag() {
                    public void drag(int mx, int my) {
                        sl.set(sl.min + (sl.max - sl.min) * Math.max(0, Math.min(1, (mx - tx) / (double) tw)));
                        c.markDirty();
                    }
                };
                drag("sl:" + s.id, tx - 4, Math.max(y, top), right + 4, Math.min(y + rowH, bottom), new Click() {
                    public void click(int mx, int my, int b) {
                        d.drag(mx, my);
                    }
                }, d, null);
            }
        } else if (s instanceof Setting.Choice) {
            drawChoice(g, (Setting.Choice) s, right, y, mid, top, bottom, vis);
        } else if (s instanceof Setting.Color) {
            final Setting.Color col = (Setting.Color) s;
            String hexText = col.alpha ? String.format("#%08X", col.argb) : String.format("#%06X", col.argb & 0xFFFFFF);
            int sw = 18, sx = right - sw;
            checker(g, sx, mid - 6, sx + sw, mid + 6);
            g.rect(sx, mid - 6, sx + sw, mid + 6, col.argb);
            Draw.outline(g, sx, mid - 6, sx + sw, mid + 6, t.line);
            g.text(hexText, sx - 6 - g.textWidth(hexText), mid - 4, t.muted, false);
            if (vis) hit("c:" + s.id, x, Math.max(y, top), right, Math.min(y + rowH, bottom), new Click() {
                public void click(int mx, int my, int b) {
                    if (picking == col) {
                        picking = null;
                    } else {
                        picking = col;
                        hex.text = col.alpha ? String.format("%08X", col.argb) : String.format("%06X", col.argb & 0xFFFFFF);
                    }
                }
            });
            if (picking == col) {
                y += rowH;
                return drawPicker(g, col, x, y, w, top, bottom);
            }
        } else if (s instanceof Setting.KeyBind) {
            final Setting.KeyBind kb = (Setting.KeyBind) s;
            String name = listening == kb ? "Press a key..." : kb.code == -1 ? "None" : c.game.keyName(kb.code);
            int bw = Math.max(70, g.textWidth(name) + 16), bx = right - bw;
            boolean on = listening == kb;
            Draw.round(g, bx, mid - 7, right, mid + 7, 3, on ? t.accent : t.surface);
            if (!on) Draw.outline(g, bx, mid - 7, right, mid + 7, t.line);
            Draw.centered(g, name, bx + bw / 2, mid - 4, on ? t.onAccent : t.text, false);
            if (vis) hit("k:" + s.id, bx, mid - 7, right, mid + 7, new Click() {
                public void click(int mx, int my, int b) {
                    if (b == 1) {
                        kb.code = -1;
                        listening = null;
                        c.markDirty();
                    } else {
                        listening = listening == kb ? null : kb;
                    }
                }
            });
        }
        y += rowH;
        if (s.hint != null) {
            g.text(Draw.fit(g, s.hint, w), x, y - 4, t.faint, false);
            y += 10;
        }
        return y;
    }

    /** Theme picker: one swatch per theme, each showing its panel colour and accent. */
    private void drawThemePicker(Gfx g, final Setting.Choice ch, int right, int mid, boolean vis) {
        int sw = 18, gap = 5, n = ch.options.length;
        int x = right - n * sw - (n - 1) * gap;
        String hoverName = null;
        for (int k = 0; k < n; k++) {
            final int idx = k;
            String o = ch.options[k];
            boolean match = Appearance.MATCH.equals(o);
            Theme th = Theme.named(match ? Appearance.launcherTheme() : o, 0);
            int x1 = x, y1 = mid - sw / 2, x2 = x + sw, y2 = mid + sw / 2;
            boolean sel = ch.index == k, hov = hovered(x1, y1, x2, y2);
            if (sel) Draw.round(g, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 6, t.accent);
            else if (hov) Draw.round(g, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 6, t.line);
            Draw.round(g, x1, y1, x2, y2, 4, th.bg | 0xFF000000);
            Draw.outline(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Draw.withAlpha(th.text, 40));
            if (match) {
                // "Match launcher": a small link mark instead of a dot.
                g.rect(x1 + 5, mid - 1, x2 - 5, mid + 1, th.accent);
                g.rect(x1 + 5, mid - 3, x1 + 7, mid + 3, th.accent);
                g.rect(x2 - 7, mid - 3, x2 - 5, mid + 3, th.accent);
            } else {
                Draw.round(g, x1 + 5, y1 + 5, x2 - 5, y2 - 5, 4, th.accent);
            }
            if (hov) hoverName = o;
            if (vis) hit("th:" + k, x1 - 2, y1 - 2, x2 + 2, y2 + 2, new Click() {
                public void click(int mx, int my, int b) {
                    ch.index = idx;
                    c.markDirty();
                }
            });
            x += sw + gap;
        }
        String label = hoverName != null ? hoverName : ch.current();
        if (Appearance.MATCH.equals(label)) label = "Match launcher (" + Appearance.launcherTheme() + ")";
        int lx = right - n * sw - (n - 1) * gap - 10 - g.textWidth(label);
        g.text(label, lx, mid - 4, hoverName != null ? t.text : t.muted, false);
    }

    private void drawChoice(Gfx g, final Setting.Choice ch, int right, int y, int mid, int top, int bottom, boolean vis) {
        if (ch == c.appearance.theme) {
            drawThemePicker(g, ch, right, mid, vis);
            return;
        }
        int total = 0;
        for (String o : ch.options) total += g.textWidth(o) + 12;
        if (ch.options.length <= 3 && total <= 190) {
            int x = right - total;
            Draw.round(g, x - 1, mid - 8, right + 1, mid + 8, 3, t.line);
            for (int k = 0; k < ch.options.length; k++) {
                final int idx = k;
                String o = ch.options[k];
                int ow = g.textWidth(o) + 12;
                boolean sel = ch.index == k;
                Draw.round(g, x, mid - 7, x + ow, mid + 7, 3, sel ? t.accent : t.surface);
                if (!sel && hovered(x, mid - 7, x + ow, mid + 7)) Draw.round(g, x, mid - 7, x + ow, mid + 7, 3, t.hover);
                g.text(o, x + 6, mid - 4, sel ? t.onAccent : t.text, false);
                if (vis) hit("ch:" + ch.id + k, x, mid - 7, x + ow, mid + 7, new Click() {
                    public void click(int mx, int my, int b) {
                        ch.index = idx;
                        c.markDirty();
                    }
                });
                x += ow;
            }
        } else {
            int bw = 120, bx = right - bw;
            Draw.round(g, bx, mid - 7, right, mid + 7, 3, t.surface);
            Draw.outline(g, bx, mid - 7, right, mid + 7, t.line);
            g.text("<", bx + 5, mid - 4, t.muted, false);
            g.text(">", right - 10, mid - 4, t.muted, false);
            Draw.centered(g, ch.current(), bx + bw / 2, mid - 4, t.text, false);
            if (vis) {
                hit("chl:" + ch.id, bx, mid - 7, bx + bw / 2, mid + 7, new Click() {
                    public void click(int mx, int my, int b) {
                        ch.index = (ch.index + ch.options.length - 1) % ch.options.length;
                        c.markDirty();
                    }
                });
                hit("chr:" + ch.id, bx + bw / 2, mid - 7, right, mid + 7, new Click() {
                    public void click(int mx, int my, int b) {
                        ch.index = (ch.index + 1) % ch.options.length;
                        c.markDirty();
                    }
                });
            }
        }
    }

    private void checker(Gfx g, int x1, int y1, int x2, int y2) {
        for (int yy = y1; yy < y2; yy += 3)
            for (int xx = x1; xx < x2; xx += 3)
                g.rect(xx, yy, Math.min(xx + 3, x2), Math.min(yy + 3, y2), ((xx - x1) / 3 + (yy - y1) / 3) % 2 == 0 ? 0xFFCCCCCC : 0xFF999999);
    }

    // ---------------------------------------------------------------- colour picker

    private int drawPicker(Gfx g, final Setting.Color col, int x, int y, int w, int top, int bottom) {
        final float[] hsv = toHsv(col.argb);
        final int alpha = col.argb >>> 24;
        int size = 60, cell = 4;
        final int sx = x + 8;
        final int sy = y + 2;
        Draw.round(g, x, y, x + w, y + size + 6, 3, t.surface);
        // Saturation/value square
        for (int i = 0; i < size; i += cell) {
            for (int j = 0; j < size; j += cell) {
                float sat = (i + cell / 2f) / size, val = 1 - (j + cell / 2f) / size;
                g.rect(sx + i, sy + j, sx + i + cell, sy + j + cell, 0xFF000000 | hsvToRgb(hsv[0], sat, val));
            }
        }
        int mx = sx + Math.round(hsv[1] * size), my = sy + Math.round((1 - hsv[2]) * size);
        Draw.outline(g, mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
        // Hue bar
        final int hx = sx + size + 6;
        for (int j = 0; j < size; j += 2) g.rect(hx, sy + j, hx + 8, sy + j + 2, 0xFF000000 | hsvToRgb(j / (float) size, 1, 1));
        int hy = sy + Math.round(hsv[0] * size);
        g.rect(hx - 1, hy - 1, hx + 9, hy + 1, 0xFFFFFFFF);
        // Alpha bar
        final int ax = hx + 14;
        if (col.alpha) {
            checker(g, ax, sy, ax + 8, sy + size);
            for (int j = 0; j < size; j += 2) {
                int a = 255 - j * 255 / size;
                g.rect(ax, sy + j, ax + 8, sy + j + 2, (a << 24) | (col.argb & 0xFFFFFF));
            }
            int ay = sy + (255 - alpha) * size / 255;
            g.rect(ax - 1, ay - 1, ax + 9, ay + 1, 0xFFFFFFFF);
        }
        // Hex entry
        int fx = (col.alpha ? ax + 14 : hx + 14) + 4;
        g.text("Hex", fx, sy + 2, t.muted, false);
        textField(g, hex, "", fx, sy + 14, 70, 16);
        boolean vis = visibleRow(sy, sy + size, top, bottom);
        if (vis) {
            final int fsize = size;
            Drag sv = new Drag() {
                public void drag(int mxx, int myy) {
                    float s = clamp((mxx - sx) / (float) fsize), v = 1 - clamp((myy - sy) / (float) fsize);
                    float[] cur = toHsv(col.argb);
                    float hue = cur[1] < 0.01f || cur[2] < 0.01f ? hsv[0] : cur[0];
                    set(col, (col.argb & 0xFF000000) | hsvToRgb(hue, s, v));
                }
            };
            Drag hue = new Drag() {
                public void drag(int mxx, int myy) {
                    float[] cur = toHsv(col.argb);
                    set(col, (col.argb & 0xFF000000) | hsvToRgb(clamp((myy - sy) / (float) fsize), Math.max(cur[1], 0.01f), Math.max(cur[2], 0.01f)));
                }
            };
            dragArea("sv", sx, sy, sx + size, sy + size, sv);
            dragArea("hue", hx, sy, hx + 8, sy + size, hue);
            if (col.alpha) {
                dragArea("alpha", ax, sy, ax + 8, sy + size, new Drag() {
                    public void drag(int mxx, int myy) {
                        int a = 255 - (int) (clamp((myy - sy) / (float) fsize) * 255);
                        set(col, (a << 24) | (col.argb & 0xFFFFFF));
                    }
                });
            }
        }
        return y + size + 12;
    }

    private void dragArea(String id, int x1, int y1, int x2, int y2, final Drag d) {
        drag(id, x1, y1, x2, y2, new Click() {
            public void click(int mx, int my, int b) {
                d.drag(mx, my);
            }
        }, d, null);
    }

    private void set(Setting.Color col, int argb) {
        col.argb = argb;
        hex.text = col.alpha ? String.format("%08X", argb) : String.format("%06X", argb & 0xFFFFFF);
        c.markDirty();
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    static float[] toHsv(int argb) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    static int hsvToRgb(float h, float s, float v) {
        float r, g, b;
        float hh = (h % 1f) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        switch (i) {
            case 0: r = v; g = u; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = u; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = u; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(int mx, int my, double amount) {
        scrollTarget -= amount * 24;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int special) {
        if (listening != null) {
            if (special == Keys.ESCAPE) {
                listening = null;
            } else {
                listening.code = keyCode;
                listening = null;
                c.markDirty();
            }
            return true;
        }
        if (super.keyPressed(keyCode, special)) return true;
        if (special == Keys.NONE && focused == null && open == null && keyCode == c.appearance.menuKey.code) {
            c.game.closeScreen();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        if (listening != null) return true;
        if (focused == hex && !Character.isLetterOrDigit(ch)) return true;
        if (focused == null && open == null && ch > ' ') {
            // Typing anywhere on the list starts a search.
            focused = search;
        }
        return super.charTyped(ch);
    }

    @Override
    public boolean onEscape() {
        if (listening != null) {
            listening = null;
            return false;
        }
        if (picking != null) {
            picking = null;
            return false;
        }
        if (focused != null) {
            focused = null;
            return false;
        }
        if (open != null && open != c.appearance) {
            open = null;
            scroll = scrollTarget = 0;
            return false;
        }
        if (!search.text.isEmpty()) {
            search.text = "";
            return false;
        }
        return true;
    }
}
