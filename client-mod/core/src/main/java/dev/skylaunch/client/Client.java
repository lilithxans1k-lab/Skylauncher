package dev.skylaunch.client;

import dev.skylaunch.client.config.Json;
import dev.skylaunch.client.features.BasicHud;
import dev.skylaunch.client.features.CombatQol;
import dev.skylaunch.client.features.Keystrokes;
import dev.skylaunch.client.features.SkyBlockHud;
import dev.skylaunch.client.features.Visual;
import dev.skylaunch.client.hud.HudModule;
import dev.skylaunch.client.module.Appearance;
import dev.skylaunch.client.module.Module;
import dev.skylaunch.client.module.Setting;
import dev.skylaunch.client.sb.SkyBlock;
import dev.skylaunch.client.ui.HudEditorScreen;
import dev.skylaunch.client.ui.MenuScreen;
import dev.skylaunch.client.ui.UiScreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The SkyLaunch in-game client. Each Minecraft version creates one of these, hands it a
 * {@link Game}, and forwards ticks, HUD rendering and input.
 */
public final class Client {
    public static final String NAME = "SkyLaunch";
    public static final String VERSION = "0.1.0";
    private static Client instance;

    public final Game game;
    public final List<Module> modules = new ArrayList<Module>();
    public final Appearance appearance = new Appearance();
    public final SkyBlock sb = new SkyBlock();
    public final Cps cps = new Cps();

    public final CombatQol.Zoom zoom;
    public final CombatQol.ToggleSprint toggleSprint;
    public final Visual.Fullbright fullbright;
    public final Visual.Crosshair crosshair;
    public final Visual.HitColour hitColour;
    public final Visual.BlockOutline blockOutline;

    /** The SkyLaunch screen currently open, if any. */
    public UiScreen screenOpen;

    private int tickCount;
    private boolean dirty;
    private long lastSave;

    public Client(Game game) {
        this.game = game;
        instance = this;
        add(new BasicHud.Fps());
        add(new BasicHud.Cps());
        add(new BasicHud.Ping());
        add(new BasicHud.Coords());
        add(new BasicHud.Clock());
        add(new Keystrokes());
        CombatQol.Armor armor = new CombatQol.Armor();
        add(armor);
        add(new CombatQol.Effects());
        zoom = add(new CombatQol.Zoom());
        toggleSprint = add(new CombatQol.ToggleSprint());
        add(new SkyBlockHud.Stats());
        add(new SkyBlockHud.Bars());
        add(new SkyBlockHud.Skills());
        fullbright = add(new Visual.Fullbright());
        crosshair = add(new Visual.Crosshair());
        hitColour = add(new Visual.HitColour());
        blockOutline = add(new Visual.BlockOutline());
        add(appearance);
        for (Module m : modules)
            for (Setting s : m.settings)
                if (s instanceof Setting.KeyBind) ((Setting.KeyBind) s).resolveDefault(game);
        loadConfig();
    }

    public static Client get() {
        return instance;
    }

    private <T extends Module> T add(T m) {
        modules.add(m);
        return m;
    }

    public List<HudModule> hudModules() {
        List<HudModule> l = new ArrayList<HudModule>();
        for (Module m : modules) if (m instanceof HudModule) l.add((HudModule) m);
        return l;
    }

    // --- events from the version layer -------------------------------------------

    /** Every client tick (20/s). */
    public void tick() {
        tickCount++;
        if (game.inWorld()) {
            if (tickCount % 10 == 0) {
                sb.readScoreboard(game.scoreboardTitle(), game.scoreboardLines());
                if (sb.onSkyBlock) sb.readTab(game.tabLines());
            }
        } else if (sb.onSkyBlock) {
            sb.reset();
        }
        for (Module m : modules) m.tick(this);
        if (dirty && System.currentTimeMillis() - lastSave > 2000) saveConfig();
    }

    /** Draw HUD widgets. Call after vanilla HUD, skip while F1 hides the HUD. */
    public void renderHud(Gfx g) {
        if (screenOpen instanceof HudEditorScreen) return;
        for (HudModule h : hudModules()) {
            if (!h.enabled) continue;
            h.editing = false;
            if (!h.active(this)) continue;
            h.render(g, this);
        }
    }

    /** Action bar text (the line above the hotbar), raw with formatting codes. */
    public void onActionBar(String raw) {
        sb.readActionBar(raw, System.currentTimeMillis());
    }

    /** Mouse button pressed in the world (0 = left, 1 = right). */
    public void onMouseClick(int button) {
        cps.click(button);
    }

    /** Scroll in the world. Returns true when SkyLaunch used it (zoom). */
    public boolean onScroll(double amount) {
        return zoom.scrolled(this, amount);
    }

    /** A key was pressed while no screen was open. Returns true if SkyLaunch handled it. */
    public boolean onKey(int keyCode) {
        if (keyCode == -1) return false;
        if (keyCode == appearance.menuKey.code) {
            openMenu();
            return true;
        }
        if (toggleSprint.enabled && keyCode == toggleSprint.key.code) {
            toggleSprint.pressed();
            return true;
        }
        return false;
    }

    public void openMenu() {
        game.openScreen(new MenuScreen(this));
    }

    public void openHudEditor() {
        game.openScreen(new HudEditorScreen(this));
    }

    // --- config ------------------------------------------------------------------

    public void markDirty() {
        dirty = true;
    }

    private File configFile() {
        return new File(game.configDir(), "skylaunch-client.json");
    }

    @SuppressWarnings("unchecked")
    public void loadConfig() {
        File f = configFile();
        if (!f.exists()) return;
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), Charset.forName("UTF-8"));
            StringBuilder b = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
            r.close();
            Object root = Json.parse(b.toString());
            if (!(root instanceof Map)) return;
            Object mods = ((Map<String, Object>) root).get("modules");
            if (!(mods instanceof Map)) return;
            for (Module m : modules) {
                Object raw = ((Map<String, Object>) mods).get(m.id);
                if (raw instanceof Map) m.load((Map<String, Object>) raw);
            }
        } catch (Exception e) {
            System.err.println("[SkyLaunch] Couldn't read " + f + ", using defaults: " + e);
        }
    }

    public void saveConfig() {
        dirty = false;
        lastSave = System.currentTimeMillis();
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", VERSION);
        Map<String, Object> mods = new LinkedHashMap<String, Object>();
        for (Module m : modules) mods.put(m.id, m.save());
        root.put("modules", mods);
        File f = configFile();
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("UTF-8"));
            w.write(Json.write(root));
            w.close();
            if (f.exists() && !f.delete()) throw new java.io.IOException("can't replace " + f);
            if (!tmp.renameTo(f)) throw new java.io.IOException("can't rename " + tmp);
        } catch (Exception e) {
            System.err.println("[SkyLaunch] Couldn't save settings: " + e);
        }
    }

    /** Clicks per second for the left and right mouse buttons. */
    public static final class Cps {
        private final Deque<Long> left = new ArrayDeque<Long>(), right = new ArrayDeque<Long>();

        void click(int button) {
            Deque<Long> d = button == 0 ? left : button == 1 ? right : null;
            if (d != null) d.addLast(System.currentTimeMillis());
        }

        private int count(Deque<Long> d) {
            long now = System.currentTimeMillis();
            while (!d.isEmpty() && now - d.peekFirst() > 1000) d.removeFirst();
            return d.size();
        }

        public int left() {
            return count(left);
        }

        public int right() {
            return count(right);
        }
    }

    public List<Module> byCategory(Module.Category cat) {
        List<Module> l = new ArrayList<Module>();
        for (Module m : modules) if (m.category == cat) l.add(m);
        return Collections.unmodifiableList(l);
    }
}
