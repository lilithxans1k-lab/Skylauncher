package dev.skylaunch.client;

/**
 * Drawing surface. Each Minecraft version implements this on top of its own GUI renderer,
 * so menus and HUD widgets are written once. Coordinates are in GUI-scaled pixels.
 * Colours are ARGB ints (0xAARRGGBB); alpha 0 draws nothing.
 */
public interface Gfx {
    int width();

    int height();

    void rect(int x1, int y1, int x2, int y2, int argb);

    void text(String s, int x, int y, int argb, boolean shadow);

    int textWidth(String s);

    /** Height of one line of text (9 for the vanilla font). */
    int lineHeight();

    /** Moves the origin to (x, y) and scales everything drawn until {@link #pop()}. */
    void push(float x, float y, float scale);

    void pop();

    /** Clip drawing to a rectangle (in current, unscaled screen coordinates). */
    void clip(int x1, int y1, int x2, int y2);

    void unclip();

    /** Draws an item icon (16x16). The handle comes from {@link Game}. Optional. */
    void item(Object itemHandle, int x, int y);
}
