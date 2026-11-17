package dev.henny.lyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.henny.lyrics.Compat;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
//?} else
/*import net.minecraft.client.renderer.MultiBufferSource;*/

/**
 * Where world text goes. Up to 1.21.8 text is drawn straight into a buffer source; from 1.21.9 on the game collects
 * "submits" for the frame and draws them later, so text is handed to the submit collector instead.
 */
public final class WorldSink {
    //? if >=1.21.9 {
    private final OrderedSubmitNodeCollector out;

    public WorldSink(OrderedSubmitNodeCollector out) {
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
}
