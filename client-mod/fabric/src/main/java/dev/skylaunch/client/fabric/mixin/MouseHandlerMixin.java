package dev.skylaunch.client.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.skylaunch.client.fabric.SkyLaunchFabric;
import net.minecraft.client.MouseHandler;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll while zooming changes the zoom instead of the hotbar slot. Same hook Zoomify uses. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
    private void skylaunch$zoomScroll(CallbackInfo ci, @Local Vector2i scroll) {
        int delta = scroll.y + scroll.x;
        if (delta != 0 && SkyLaunchFabric.client() != null && SkyLaunchFabric.client().onScroll(delta)) ci.cancel();
    }
}
