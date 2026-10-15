package dev.skylaunch.client.forge.mixin;

import dev.skylaunch.client.forge.SkyLaunchForge;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hurt camera: skip the tilt Minecraft applies when you take damage. */
@Mixin(EntityRenderer.class)
public class MixinEntityRendererCamera {
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void skylaunch$noHurtCam(float partialTicks, CallbackInfo ci) {
        if (SkyLaunchForge.client() != null && SkyLaunchForge.client().noHurtCam.enabled) ci.cancel();
    }
}
