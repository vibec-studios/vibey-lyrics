package dev.henny.lyrics.mixin;

import dev.henny.lyrics.sky.SkyLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.1 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
//? if >=1.21.6 <26.1 {
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
//? if >=1.21.2 <1.21.6 {
/*import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}
//? if <1.21.2 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Custom skies: the fog, and the color behind everything, take the sky's horizon color, so the land fades into the
 * sky instead of into a blue band. Only while a custom sky is on and you are not under water or in lava.
 */
@Mixin(FogRenderer.class)
public abstract class FogMixin {
    //? if >=26.1 {
    /*@Inject(method = "computeFogColor", at = @At("TAIL"))
    private void vibeylyrics$skyFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, Vector4f out, CallbackInfo ci) {
        int c = SkyLook.fogOverride();
        if (c != -1) out.set(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, out.w);
    }
    *///?}
    //? if >=1.21.6 <26.1 {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void vibeylyrics$skyFog(CallbackInfoReturnable<Vector4f> cir) {
        int c = SkyLook.fogOverride();
        Vector4f out = cir.getReturnValue();
        if (c != -1 && out != null) out.set(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, out.w);
    }
    //?}
    //? if >=1.21.2 <1.21.6 {
    /*@Inject(method = "computeFogColor", at = @At("RETURN"))
    private static void vibeylyrics$skyFog(CallbackInfoReturnable<Vector4f> cir) {
        int c = SkyLook.fogOverride();
        Vector4f out = cir.getReturnValue();
        if (c != -1 && out != null) out.set(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, out.w);
    }
    *///?}
    //? if <1.21.2 {
    /*@Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void vibeylyrics$skyFog(CallbackInfo ci) {
        int c = SkyLook.fogOverride();
        if (c == -1) return;
        fogRed = ((c >> 16) & 255) / 255f;
        fogGreen = ((c >> 8) & 255) / 255f;
        fogBlue = (c & 255) / 255f;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }
    *///?}
}
