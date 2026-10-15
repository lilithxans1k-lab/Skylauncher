package dev.skylaunch.client.forge.mixin;

import dev.skylaunch.client.forge.SkyLaunchForge;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: divide the field of view while the zoom key is held. */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void skylaunch$zoom(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        if (SkyLaunchForge.client() == null) return;
        double d = SkyLaunchForge.client().zoom.divisor(partialTicks);
        if (d != 1) cir.setReturnValue((float) (cir.getReturnValue() / d));
    }
}
