package dev.skylaunch.client.fabric;

import dev.skylaunch.client.Client;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Recolours the damage tint by rewriting the top half of Minecraft's 16x16 overlay texture.
 * Minecraft 26.x ships with readable names, so this looks the pieces up at runtime instead of
 * compiling against them: if a future version moves them, only this feature switches off
 * (with one log line) and the rest of the mod keeps working.
 */
final class HitColourHook {
    private static final int VANILLA = 0xB2FF0000;
    private static int applied = VANILLA;
    private static boolean broken;

    private HitColourHook() {}

    static void update(Client c) {
        if (broken) return;
        int want = c.hitColour.enabled ? c.hitColour.colour.argb : VANILLA;
        if (want == applied) return;
        try {
            Object renderer = Minecraft.getInstance().gameRenderer;
            Object overlay = call(renderer, "overlayTexture");
            Object texture = field(overlay, "texture");
            Object pixels = call(texture, "getPixels");
            Method set = pixels.getClass().getMethod("setPixel", int.class, int.class, int.class);
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 16; x++) set.invoke(pixels, x, y, want);
            call(texture, "upload");
            applied = want;
        } catch (Throwable t) {
            broken = true;
            System.err.println("[SkyLaunch] Hit colour isn't supported on this Minecraft version: " + t);
        }
    }

    private static Object call(Object target, String name) throws Exception {
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object r = m.invoke(target);
                    if (r == null) throw new IllegalStateException(name + " returned null");
                    return r;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
