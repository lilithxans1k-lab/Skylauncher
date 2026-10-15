package dev.skylaunch.client.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.skylaunch.client.fabric.SkyLaunchFabric;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: divide the field of view while the zoom key is held. Same hook Zoomify uses on 26.x. */
@Mixin(Camera.class)
public class CameraMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float skylaunch$zoom(float fov, @Local(name = "partialTicks", argsOnly = true) float partialTicks) {
        if (SkyLaunchFabric.client() == null) return fov;
        return (float) (fov / SkyLaunchFabric.client().zoom.divisor(partialTicks));
    }
}
