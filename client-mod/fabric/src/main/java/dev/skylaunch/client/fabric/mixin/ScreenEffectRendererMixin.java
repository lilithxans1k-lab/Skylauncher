package dev.skylaunch.client.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skylaunch.client.fabric.SkyLaunchFabric;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No fire overlay. Same hook Odin uses on 26.2. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void skylaunch$noFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (SkyLaunchFabric.client() != null && SkyLaunchFabric.client().noFireOverlay.enabled) ci.cancel();
    }
}
