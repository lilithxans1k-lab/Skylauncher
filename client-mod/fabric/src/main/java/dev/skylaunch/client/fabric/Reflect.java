package dev.skylaunch.client.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Looks up a few game values by name at runtime (Minecraft 26.x ships with readable names).
 * Used where a name isn't confirmed for this version: a miss returns null instead of
 * breaking the whole mod at build time.
 */
final class Reflect {
    private Reflect() {}

    static Object call(Object target, String name) {
        if (target == null) return null;
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(target);
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static Object field(Object target, String name) {
        if (target == null) return null;
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
