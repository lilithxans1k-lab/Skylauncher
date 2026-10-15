package dev.skylaunch.client.module;

/**
 * One option on a module's settings page. Subclasses below cover every control the menu draws.
 */
public abstract class Setting {
    public final String id;
    public final String name;
    /** Settings with the same section are grouped under one heading. */
    public String section = null;
    public String hint = null;
    private Visibility visible = null;

    public interface Visibility {
        boolean visible();
    }

    protected Setting(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Setting in(String section) {
        this.section = section;
        return this;
    }

    public Setting hint(String hint) {
        this.hint = hint;
        return this;
    }

    public Setting showIf(Visibility v) {
        this.visible = v;
        return this;
    }

    public boolean isVisible() {
        return visible == null || visible.visible();
    }

    public abstract Object save();

    public abstract void load(Object raw);

    public abstract void reset();

    // ------------------------------------------------------------------------

    public static class Toggle extends Setting {
        public boolean value;
        private boolean def;

        public Toggle(String id, String name, boolean def) {
            super(id, name);
            this.value = this.def = def;
        }

        /** Changes the default (and current value) after construction. */
        public void setDefault(boolean v) { def = value = v; }

        public Object save() { return value; }

        public void load(Object raw) { if (raw instanceof Boolean) value = (Boolean) raw; }

        public void reset() { value = def; }
    }

    public static class Slider extends Setting {
        public double value;
        public final double min, max, step;
        public final String suffix;
        private final double def;

        public Slider(String id, String name, double def, double min, double max, double step, String suffix) {
            super(id, name);
            this.value = this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix == null ? "" : suffix;
        }

        public void set(double v) {
            double snapped = Math.round((v - min) / step) * step + min;
            value = Math.max(min, Math.min(max, snapped));
        }

        public String display() {
            String n = step >= 1 ? String.valueOf((long) Math.round(value)) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
            return n + suffix;
        }

        public Object save() { return value; }

        public void load(Object raw) { if (raw instanceof Number) set(((Number) raw).doubleValue()); }

        public void reset() { value = def; }
    }

    public static class Color extends Setting {
        public int argb;
        public final boolean alpha;
        private final int def;

        public Color(String id, String name, int def, boolean alpha) {
            super(id, name);
            this.argb = this.def = def;
            this.alpha = alpha;
        }

        public Object save() { return String.format("#%08X", argb); }

        public void load(Object raw) {
            if (!(raw instanceof String)) return;
            String s = ((String) raw).replace("#", "");
            try {
                long v = Long.parseLong(s, 16);
                argb = s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
            } catch (NumberFormatException ignored) {
                // keep current value
            }
        }

        public void reset() { argb = def; }
    }

    public static class Choice extends Setting {
        public final String[] options;
        public int index;
        private int def;

        public Choice(String id, String name, int def, String... options) {
            super(id, name);
            this.options = options;
            this.index = this.def = def;
        }

        public String current() { return options[index]; }

        /** Changes the default (and current value) after construction. */
        public void setDefault(String option) {
            for (int k = 0; k < options.length; k++) if (options[k].equals(option)) def = index = k;
        }

        public boolean is(String option) { return options[index].equals(option); }

        public Object save() { return options[index]; }

        public void load(Object raw) {
            for (int k = 0; k < options.length; k++) if (options[k].equals(raw)) index = k;
        }

        public void reset() { index = def; }
    }

    public static class KeyBind extends Setting {
        /** In the running version's own key-code scheme. -1 means unbound. */
        public int code;
        private final String defName;
        private int def = Integer.MIN_VALUE;
        private boolean loaded = false;

        public KeyBind(String id, String name, String defaultKeyName) {
            super(id, name);
            this.defName = defaultKeyName;
            this.code = -1;
        }

        /** Resolves the default key once the game tells us its key codes. */
        public void resolveDefault(dev.skylaunch.client.Game game) {
            if (def == Integer.MIN_VALUE) {
                def = defName == null ? -1 : game.key(defName);
                if (!loaded) code = def;
            }
        }

        public Object save() { return code; }

        public void load(Object raw) {
            if (raw instanceof Number) { code = ((Number) raw).intValue(); loaded = true; }
        }

        public void reset() { code = def == Integer.MIN_VALUE ? -1 : def; }
    }
}
