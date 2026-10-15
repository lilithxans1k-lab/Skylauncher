package dev.skylaunch.client.fabric.mixin;

import dev.skylaunch.client.fabric.SkyLaunchFabric;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts left and right clicks for the CPS display. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void skylaunch$leftClick(CallbackInfoReturnable<Boolean> cir) {
        if (SkyLaunchFabric.client() == null) return;
        SkyLaunchFabric.client().onMouseClick(0);
        Minecraft mc = (Minecraft) (Object) this;
        // Reach display and combo counter: you swung at an entity.
        if (mc.hitResult instanceof EntityHitResult hit && mc.player != null) {
            SkyLaunchFabric.client().onAttackEntity(mc.player.getEyePosition().distanceTo(hit.getLocation()));
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void skylaunch$rightClick(CallbackInfo ci) {
        if (SkyLaunchFabric.client() != null) SkyLaunchFabric.client().onMouseClick(1);
    }
}
