package dev.skylaunch.client.module;

import dev.skylaunch.client.ui.Theme;

/** Menu theme, menu key and the default look every HUD element inherits. */
public final class Appearance extends Module {
    public final Setting.Choice theme;
    public static final String MATCH = "Match launcher";
    public final Setting.Toggle customAccent;
    public final Setting.Color accent;
    public final Setting.KeyBind menuKey;
    public final Setting.Toggle blur;
    /** Set by the version layer: whether it can blur the game behind menus. */
    public static boolean canBlur = true;

    public final Setting.Color text;
    public final Setting.Color label;
    public final Setting.Choice background;
    public final Setting.Color backgroundColor;
    public final Setting.Toggle rounded;
    public final Setting.Slider padding;
    public final Setting.Toggle shadow;
    public final Setting.Toggle onlyInSkyBlock;

    public Appearance() {
        super("appearance", "Appearance", "Menu theme and the default look of every HUD element.", Category.SETTINGS, true);
        toggleable = false;
        theme = add(new Setting.Choice("theme", "Menu theme", 0, MATCH, "Midnight", "Dawn", "Violet", "Light"));
        theme.in("Menu");
        customAccent = add(new Setting.Toggle("custom_accent", "Use my own accent colour", false));
        customAccent.in("Menu");
        accent = add(new Setting.Color("accent", "Accent colour", 0xFF4CC2FF, false));
        accent.in("Menu").showIf(new Setting.Visibility() {
            public boolean visible() { return customAccent.value; }
        });
        menuKey = add(new Setting.KeyBind("menu_key", "Open this menu", "RIGHT_SHIFT"));
        menuKey.in("Menu");
        blur = add(new Setting.Toggle("blur", "Blur the game behind menus", true));
        blur.in("Menu").showIf(() -> canBlur);

        text = add(new Setting.Color("text", "Value colour", 0xFFFFFFFF, true));
        text.in("HUD defaults");
        label = add(new Setting.Color("label", "Label colour", 0xFFB4B4B4, true));
        label.in("HUD defaults");
        background = add(new Setting.Choice("background", "Background", 1, "None", "Box"));
        background.in("HUD defaults");
        backgroundColor = add(new Setting.Color("bg_color", "Background colour", 0x8C101012, true));
        backgroundColor.in("HUD defaults").showIf(new Setting.Visibility() {
            public boolean visible() { return background.is("Box"); }
        });
        rounded = add(new Setting.Toggle("rounded", "Rounded corners", true));
        rounded.in("HUD defaults").showIf(new Setting.Visibility() {
            public boolean visible() { return background.is("Box"); }
        });
        padding = add(new Setting.Slider("padding", "Padding", 4, 0, 10, 1, "px"));
        padding.in("HUD defaults");
        shadow = add(new Setting.Toggle("shadow", "Text shadow", false));
        shadow.in("HUD defaults");
        onlyInSkyBlock = add(new Setting.Toggle("only_skyblock", "SkyBlock widgets only on SkyBlock", true));
        onlyInSkyBlock.in("HUD defaults");
    }

    /** The theme the launcher was using when it started the game (passed as -Dskylaunch.theme). */
    public static String launcherTheme() {
        String s = null;
        try {
            s = System.getProperty("skylaunch.theme");
        } catch (SecurityException ignored) {
        }
        if (s != null) for (String n : Theme.NAMES) if (n.equalsIgnoreCase(s.trim())) return n;
        return "Midnight";
    }

    /** The theme name in effect, with "Match launcher" resolved. */
    public String themeName() {
        return theme.is(MATCH) ? launcherTheme() : theme.current();
    }

    public Theme menuTheme() {
        return Theme.named(themeName(), customAccent.value ? accent.argb : 0);
    }
}
