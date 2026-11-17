package dev.henny.lyrics.mixin;

// Only Minecraft 1.21.9/1.21.10: Fabric API has no world render events there, so the lyrics hook into the moment the
// game collects entity submits (the same spot Fabric's BEFORE_ENTITIES uses from 1.21.11 on). Every other version
// compiles this file as empty and does not load the mixin config.
//? if >=1.21.9 <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.henny.lyrics.render.WorldLyrics;
import dev.henny.lyrics.render.WorldSink;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void vibeylyrics$submitLyrics(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        WorldLyrics.render(poseStack, new WorldSink(collector));
    }
}
*///?}
