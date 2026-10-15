package dev.skylaunch.client.forge;

import dev.skylaunch.client.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Shared drawing calls mapped onto Minecraft 1.8.9's GUI helpers. */
final class ForgeGfx implements Gfx {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final FontRenderer font = mc.fontRendererObj;
    private final ScaledResolution res = new ScaledResolution(mc);

    @Override
    public int width() {
        return res.getScaledWidth();
    }

    @Override
    public int height() {
        return res.getScaledHeight();
    }

    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
        if ((argb >>> 24) == 0 || x1 == x2 || y1 == y2) return;
        Gui.drawRect(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) == 0 || s.isEmpty()) return;
        // 1.8.9 treats alpha below 4 as fully opaque; clamp so faint text stays faint.
        if ((argb >>> 24) < 8) argb = (argb & 0xFFFFFF) | 0x08000000;
        GlStateManager.enableBlend();
        font.drawString(s, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String s) {
        return font.getStringWidth(s);
    }

    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void push(float x, float y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
    }

    @Override
    public void clip(int x1, int y1, int x2, int y2) {
        int f = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * f, mc.displayHeight - y2 * f, Math.max(0, x2 - x1) * f, Math.max(0, y2 - y1) * f);
    }

    @Override
    public void unclip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void item(Object handle, int x, int y) {
        if (!(handle instanceof ItemStack)) return;
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) handle, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
    }
}
