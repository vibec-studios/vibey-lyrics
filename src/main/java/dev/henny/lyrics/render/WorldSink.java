package dev.henny.lyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.henny.lyrics.Compat;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else
/*import net.minecraft.client.renderer.MultiBufferSource;*/

/**
 * Where world text goes. Up to 1.21.8 text is drawn straight into a buffer source; from 1.21.9 on the game collects
 * "submits" for the frame and draws them later, so text is handed to the submit collector instead.
 */
public final class WorldSink {
    /** Builds textured quads (text vertex format: position, color, uv, light). */
    public interface Mesh {
        void build(Matrix4f pose, VertexConsumer out);
    }

    //? if >=1.21.9 {
    private final SubmitNodeCollector out;

    public WorldSink(SubmitNodeCollector out) {
        this.out = out;
    }
    //?} else {
    /*private final MultiBufferSource out;

    public WorldSink(MultiBufferSource out) {
        this.out = out;
    }
    *///?}

    public void text(Font font, PoseStack pose, Component c, float x, float y, int color, int background, boolean onTop) {
        if ((color >>> 24) < 6 && (background >>> 24) == 0) return;
        Font.DisplayMode mode = onTop ? Font.DisplayMode.POLYGON_OFFSET : Font.DisplayMode.NORMAL;
        //? if >=1.21.9 {
        out.submitText(pose, x, y, c.getVisualOrderText(), false, mode, Compat.FULL_BRIGHT, color, background, 0);
        //?} else
        /*font.drawInBatch(c, x, y, color, false, pose.last().pose(), out, mode, background, Compat.FULL_BRIGHT);*/
    }

    /**
     * Textured quads drawn before everything else this sink gets (the sky has to be behind the lyrics' see-through
     * glow). Uses the text render type, which every version has: depth tested, alpha blended, full bright.
     */
    //? if >=1.21.11 {
    public void textured(PoseStack pose, Identifier texture, Mesh mesh) {
        RenderType type = RenderTypes.text(texture);
    //?} else {
    /*public void textured(PoseStack pose, ResourceLocation texture, Mesh mesh) {
        RenderType type = RenderType.text(texture);
    *///?}
        //? if >=1.21.9 {
        out.order(-1000).submitCustomGeometry(pose, type, (p, vc) -> mesh.build(p.pose(), vc));
        //?} else {
        /*mesh.build(pose.last().pose(), out.getBuffer(type));
        if (out instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        *///?}
    }
}
