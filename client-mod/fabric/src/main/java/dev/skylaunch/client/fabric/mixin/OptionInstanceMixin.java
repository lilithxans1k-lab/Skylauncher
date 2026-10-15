package dev.skylaunch.client.fabric.mixin;

import dev.skylaunch.client.Client;
import dev.skylaunch.client.fabric.SkyLaunchFabric;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides a few video options while a SkyLaunch mod is on, without touching options.txt,
 * so turning the mod off brings your own setting straight back:
 *  - Fullbright   -> very high brightness
 *  - No view bob  -> View Bobbing off
 *  - No hurt cam  -> Damage Tilt 0
 */
@Mixin(OptionInstance.class)
public class OptionInstanceMixin {
    @Shadow
    @Final
    Component caption;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void skylaunch$override(CallbackInfoReturnable<Object> cir) {
        Client c = SkyLaunchFabric.client();
        if (c == null) return;
        String key = skylaunch$key();
        if (key == null) return;
        switch (key) {
            case "options.gamma" -> { if (c.fullbright.enabled) cir.setReturnValue(15.0D); }
            case "options.viewBobbing" -> { if (c.noViewBob.enabled) cir.setReturnValue(Boolean.FALSE); }
            case "options.damageTiltStrength" -> { if (c.noHurtCam.enabled) cir.setReturnValue(0.0D); }
            default -> { }
        }
    }

    @Unique
    private String skylaunch$key() {
        return caption.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }
}
