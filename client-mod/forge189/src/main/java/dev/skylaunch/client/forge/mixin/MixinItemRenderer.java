package dev.skylaunch.client.forge.mixin;

import dev.skylaunch.client.forge.SkyLaunchForge;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No fire overlay. */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
    private void skylaunch$noFire(float partialTicks, CallbackInfo ci) {
        if (SkyLaunchForge.client() != null && SkyLaunchForge.client().noFireOverlay.enabled) ci.cancel();
    }
}
