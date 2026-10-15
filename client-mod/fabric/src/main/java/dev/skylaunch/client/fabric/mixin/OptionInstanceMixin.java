package dev.skylaunch.client.fabric.mixin;

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

/** Fullbright: report a very high brightness while it's on, without touching options.txt. */
@Mixin(OptionInstance.class)
public class OptionInstanceMixin {
    @Shadow
    @Final
    Component caption;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void skylaunch$fullbright(CallbackInfoReturnable<Object> cir) {
        if (SkyLaunchFabric.client() != null && SkyLaunchFabric.client().fullbright.enabled && skylaunch$isGamma()) {
            cir.setReturnValue(15.0D);
        }
    }

    @Unique
    private boolean skylaunch$isGamma() {
        return caption.getContents() instanceof TranslatableContents t && t.getKey().equals("options.gamma");
    }
}
