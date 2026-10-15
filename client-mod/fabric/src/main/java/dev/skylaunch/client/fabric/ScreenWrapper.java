package dev.skylaunch.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skylaunch.client.Client;
import dev.skylaunch.client.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hosts a shared {@link UiScreen} inside Minecraft 26.2's screen system. */
final class ScreenWrapper extends Screen {
    private final UiScreen ui;

    ScreenWrapper(UiScreen ui) {
        super(Component.literal("SkyLaunch"));
        this.ui = ui;
        Client.get().screenOpen = ui;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ui.render(new FabricGfx(graphics), mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Vanilla's background blurs the game behind the screen. The HUD editor needs a clear
        // view of the HUD, and the blur can be turned off under Appearance.
        if (!(ui instanceof dev.skylaunch.client.ui.HudEditorScreen) && Client.get().appearance.blur.value) {
            super.extractBackground(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return ui.mouseClicked((int) click.x(), (int) click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        ui.mouseReleased((int) click.x(), (int) click.y(), click.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        ui.mouseDragged((int) click.x(), (int) click.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return ui.mouseScrolled((int) mouseX, (int) mouseY, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        int special = switch (key) {
            case InputConstants.KEY_ESCAPE -> UiScreen.Keys.ESCAPE;
            case InputConstants.KEY_BACKSPACE -> UiScreen.Keys.BACKSPACE;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> UiScreen.Keys.ENTER;
            case InputConstants.KEY_DELETE -> UiScreen.Keys.DELETE;
            default -> UiScreen.Keys.NONE;
        };
        if (ui.keyPressed(key, special)) return true;
        if (special == UiScreen.Keys.ESCAPE) {
            if (ui.onEscape()) onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        String s = input.codepointAsString();
        boolean used = false;
        for (int i = 0; i < s.length(); i++) used |= ui.charTyped(s.charAt(i));
        return used;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        Client c = Client.get();
        if (c.screenOpen == ui) c.screenOpen = null;
        ui.onClose();
    }
}
