package dev.skylaunch.client.ui;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.Gfx;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen written once in shared code. Each version wraps it in its own Screen class and
 * forwards drawing and input here.
 *
 * Controls are immediate-mode: while drawing, each control registers the area it occupies
 * together with what should happen on click or drag. Input is then matched against the
 * areas from the previous frame.
 */
public abstract class UiScreen {
    protected final Client c;
    protected int width, height;
    protected int mouseX, mouseY;
    protected long openedAt = System.currentTimeMillis();

    private final List<Hit> hits = new ArrayList<Hit>();
    private final List<Hit> building = new ArrayList<Hit>();
    private Hit dragging;
    /** Text field that receives typed characters, if any. */
    protected TextField focused;

    protected UiScreen(Client c) {
        this.c = c;
    }

    public interface Click {
        void click(int mx, int my, int button);
    }

    public interface Drag {
        void drag(int mx, int my);
    }

    private static final class Hit {
        int x1, y1, x2, y2;
        Click click;
        Drag drag;
        Runnable release;
        String id;
    }

    /** Register a clickable area for this frame. Later registrations sit on top. */
    protected void hit(String id, int x1, int y1, int x2, int y2, Click click) {
        Hit h = new Hit();
        h.id = id;
        h.x1 = x1;
        h.y1 = y1;
        h.x2 = x2;
        h.y2 = y2;
        h.click = click;
        building.add(h);
    }

    /** Register a draggable area (sliders, HUD elements). */
    protected void drag(String id, int x1, int y1, int x2, int y2, Click press, Drag drag, Runnable release) {
        hit(id, x1, y1, x2, y2, press);
        Hit h = building.get(building.size() - 1);
        h.drag = drag;
        h.release = release;
    }

    protected boolean hovered(int x1, int y1, int x2, int y2) {
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    protected boolean isDragging(String id) {
        return dragging != null && id.equals(dragging.id);
    }

    // --- called by the version-specific wrapper --------------------------------

    public void resize(int w, int h) {
        width = w;
        height = h;
    }

    public final void render(Gfx g, int mx, int my) {
        width = g.width();
        height = g.height();
        mouseX = mx;
        mouseY = my;
        building.clear();
        draw(g);
        hits.clear();
        hits.addAll(building);
    }

    protected abstract void draw(Gfx g);

    public boolean mouseClicked(int mx, int my, int button) {
        mouseX = mx;
        mouseY = my;
        TextField before = focused;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x1 && mx < h.x2 && my >= h.y1 && my < h.y2) {
                if (h.drag != null && button == 0) dragging = h;
                if (h.click != null) h.click.click(mx, my, button);
                if (focused == before && before != null && !"field".equals(h.id)) focused = null;
                return true;
            }
        }
        focused = null;
        return clickedNothing(mx, my, button);
    }

    protected boolean clickedNothing(int mx, int my, int button) {
        return false;
    }

    public void mouseDragged(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        if (dragging != null && dragging.drag != null) dragging.drag.drag(mx, my);
    }

    public void mouseReleased(int mx, int my, int button) {
        if (dragging != null && dragging.release != null) dragging.release.run();
        dragging = null;
    }

    public boolean mouseScrolled(int mx, int my, double amount) {
        return false;
    }

    /** Key codes are the running version's; use {@link Keys} for the special keys. */
    public boolean keyPressed(int keyCode, int special) {
        if (focused != null) {
            if (special == Keys.BACKSPACE) { focused.backspace(); return true; }
            if (special == Keys.ENTER) { focused.submit(); focused = null; return true; }
            if (special == Keys.ESCAPE) { focused = null; return true; }
            if (special != Keys.NONE) return true;
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (focused != null && ch >= 32 && ch != 127) {
            focused.type(ch);
            return true;
        }
        return false;
    }

    /** Return false to stop Esc from closing the screen. */
    public boolean onEscape() {
        return true;
    }

    public void onClose() {
        c.saveConfig();
    }

    /** Small editable text buffer, used for search and hex colour entry. */
    public static class TextField {
        public String text = "";
        public final int max;

        public TextField(int max) {
            this.max = max;
        }

        public void type(char ch) {
            if (text.length() < max) text += ch;
            changed();
        }

        public void backspace() {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            changed();
        }

        public void changed() {}

        public void submit() {}
    }

    /** Special keys, translated by each version from its own key codes. */
    public static final class Keys {
        public static final int NONE = 0, ESCAPE = 1, BACKSPACE = 2, ENTER = 3, DELETE = 4;

        private Keys() {}
    }
}
