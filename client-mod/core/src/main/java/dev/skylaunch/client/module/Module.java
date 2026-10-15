package dev.skylaunch.client.module;

import dev.skylaunch.client.Client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A feature the player can switch on and configure from the menu. */
public abstract class Module {
    public enum Category {
        HUD("HUD"),
        SKYBLOCK("SkyBlock"),
        COMBAT("Combat & QoL"),
        VISUAL("Visual"),
        SETTINGS("Settings");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled;
    private final boolean defaultEnabled;
    /** False for always-on pages like Appearance, which have no switch. */
    public boolean toggleable = true;
    public final List<Setting> settings = new ArrayList<Setting>();

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = this.defaultEnabled = enabledByDefault;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable();
        else onDisable();
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Called every client tick (20 per second) while in a world, enabled or not. */
    public void tick(Client c) {}

    public void resetToDefaults() {
        enabled = defaultEnabled;
        for (Setting s : settings) s.reset();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("enabled", enabled);
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        for (Setting st : settings) s.put(st.id, st.save());
        m.put("settings", s);
        return m;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        if (m.get("enabled") instanceof Boolean) enabled = (Boolean) m.get("enabled");
        Object raw = m.get("settings");
        if (raw instanceof Map) {
            Map<String, Object> s = (Map<String, Object>) raw;
            for (Setting st : settings) if (s.containsKey(st.id)) st.load(s.get(st.id));
        }
    }
}
