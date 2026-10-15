package dev.skylaunch.client.fabric;

import dev.skylaunch.client.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Shared drawing calls mapped onto Minecraft 26.2's GUI renderer. */
final class FabricGfx implements Gfx {
    private final GuiGraphicsExtractor g;
    private final Font font = Minecraft.getInstance().font;

    FabricGfx(GuiGraphicsExtractor g) {
        this.g = g;
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
        if ((argb >>> 24) != 0) g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) != 0 && !s.isEmpty()) g.text(font, s, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String s) {
        return font.width(s);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void push(float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
    }

    @Override
    public void pop() {
        g.pose().popMatrix();
    }

    @Override
    public void clip(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void unclip() {
        g.disableScissor();
    }

    @Override
    public void item(Object handle, int x, int y) {
        if (handle instanceof ItemStack stack && !stack.isEmpty()) g.item(stack, x, y);
    }
}
