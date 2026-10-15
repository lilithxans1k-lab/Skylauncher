package dev.skylaunch.client.forge.mixin;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.forge.SkyLaunchForge;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide vanilla HUD: the sidebar has no Forge event on 1.8.9, so skip its draw call. */
@Mixin(GuiIngame.class)
public class MixinGuiIngame {
    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true, require = 0)
    private void skylaunch$hideSidebar(ScoreObjective objective, ScaledResolution res, CallbackInfo ci) {
        Client c = SkyLaunchForge.client();
        if (c != null && c.hideVanillaHud.hide(c.hideVanillaHud.sidebar)) ci.cancel();
    }
}
