package dev.henny.lyrics.mixin;

import dev.henny.lyrics.sky.SkyLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.CloudRenderer;
//?} else
/*import net.minecraft.client.renderer.LevelRenderer;*/

/**
 * Custom skies: the vanilla clouds are skipped (the sky sits closer than the far clouds, which would be cut off at
 * its edge). Can be switched off under Advanced.
 */
//? if >=1.21.2 {
@Mixin(CloudRenderer.class)
//?} else
/*@Mixin(LevelRenderer.class)*/
public abstract class CloudMixin {
    //? if >=26.3 {
    /*@Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true)
    *///?}
    //? if >=1.21.2 <26.3 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //?}
    //? if <1.21.2
    /*@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)*/
    private void vibeylyrics$hideClouds(CallbackInfo ci) {
        if (SkyLook.hideClouds()) ci.cancel();
    }
}
