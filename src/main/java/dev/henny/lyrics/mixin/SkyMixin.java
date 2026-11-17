package dev.henny.lyrics.mixin;

import dev.henny.lyrics.sky.SkyLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.SkyRenderer;
//?} else
/*import net.minecraft.client.renderer.LevelRenderer;*/

/**
 * Custom skies: the vanilla sky (its color, sun, moon, stars, sunset glow) is skipped. What shows behind the land is
 * then the clear color, which the fog mixin turns into the sky's horizon color, and the custom sky on top of it.
 */
//? if >=1.21.2 {
@Mixin(SkyRenderer.class)
//?} else
/*@Mixin(LevelRenderer.class)*/
public abstract class SkyMixin {
    //? if >=1.21.2 {
    @Inject(method = {"renderSkyDisc", "renderDarkDisc", "renderSunMoonAndStars", "renderSunriseAndSunset"}, at = @At("HEAD"), cancellable = true)
    //?} else
    /*@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)*/
    private void vibeylyrics$hideVanillaSky(CallbackInfo ci) {
        if (SkyLook.active() != null) ci.cancel();
    }
}
