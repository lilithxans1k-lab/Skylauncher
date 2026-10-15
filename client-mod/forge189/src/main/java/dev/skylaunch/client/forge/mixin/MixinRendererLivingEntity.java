package dev.skylaunch.client.forge.mixin;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.forge.SkyLaunchForge;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hit colour: the first four FloatBuffer.put calls in setBrightness write the red damage tint
 * (1, 0, 0, 0.3). Swap in the chosen colour.
 */
@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {
    private static final String PUT = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;";

    private static float channel(float original, int shift) {
        Client c = SkyLaunchForge.client();
        if (c == null || !c.hitColour.enabled) return original;
        return ((c.hitColour.colour.argb >>> shift) & 255) / 255f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 0), require = 0)
    private float skylaunch$red(float v) {
        return channel(v, 16);
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 1), require = 0)
    private float skylaunch$green(float v) {
        return channel(v, 8);
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 2), require = 0)
    private float skylaunch$blue(float v) {
        return channel(v, 0);
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = PUT, ordinal = 3), require = 0)
    private float skylaunch$alpha(float v) {
        return channel(v, 24);
    }
}
