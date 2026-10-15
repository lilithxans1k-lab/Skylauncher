package dev.skylaunch.client.forge;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.ui.UiScreen;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Hosts a shared {@link UiScreen} inside Minecraft 1.8.9's GUI system. */
final class GuiWrapper extends GuiScreen {
    private final UiScreen ui;

    GuiWrapper(UiScreen ui) {
        this.ui = ui;
        Client.get().screenOpen = ui;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ui.render(new ForgeGfx(), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        ui.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        ui.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        ui.mouseDragged(mouseX, mouseY);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            ui.mouseScrolled(mx, my, wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        int special = UiScreen.Keys.NONE;
        if (keyCode == Keyboard.KEY_ESCAPE) special = UiScreen.Keys.ESCAPE;
        else if (keyCode == Keyboard.KEY_BACK) special = UiScreen.Keys.BACKSPACE;
        else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) special = UiScreen.Keys.ENTER;
        else if (keyCode == Keyboard.KEY_DELETE) special = UiScreen.Keys.DELETE;

        if (ui.keyPressed(keyCode, special)) return;
        if (special == UiScreen.Keys.ESCAPE) {
            if (ui.onEscape()) mc.displayGuiScreen(null);
            return;
        }
        if (typedChar >= 32 && typedChar != 127) ui.charTyped(typedChar);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        Client c = Client.get();
        if (c.screenOpen == ui) c.screenOpen = null;
        ui.onClose();
    }
}
