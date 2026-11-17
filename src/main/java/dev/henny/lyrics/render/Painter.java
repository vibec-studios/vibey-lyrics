package dev.henny.lyrics.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Draws a {@link LyricLayout.Frame}: caption boxes, then per word its 3D sides (a stack of darker copies going back),
 * a soft glow (two rings of copies) and the word itself.
 */
public abstract class Painter {
    private static final float[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.71f, 0.71f}, {-0.71f, 0.71f}, {0.71f, -0.71f}, {-0.71f, -0.71f}
    };
    private static final int DEPTH_LAYERS = 6;

    protected final Font font;

    protected Painter(Font font) {
        this.font = font;
    }

    /**
     * Draws {@code c} as the word {@code r}: (ox, oy, oz) is an offset in the word's own unscaled font pixels.
     * {@code back} = a layer behind the word (glow, 3D sides).
     */
    protected abstract void draw(Component c, LyricLayout.Run r, float ox, float oy, float oz, int argb, boolean back);

    protected abstract void box(float x1, float y1, float x2, float y2, int argb);

    public void paint(LyricLayout.Frame frame) {
        for (LyricLayout.Box b : frame.boxes()) box(b.x1(), b.y1(), b.x2(), b.y2(), b.color());
        for (LyricLayout.Run r : frame.runs()) {
            Component c = r.text();
            if (r.depth() != 0) {
                int shadow = r.depth() & 0xFF000000;
                for (int k = DEPTH_LAYERS; k >= 1; k--) {
                    int col = LyricLayout.mix(r.depth(), shadow, 0.36f + 0.035f * k);
                    draw(c, r, 0.13f * k, 0.22f * k, -0.3f * k, col, true);
                }
            }
            float g = Math.min(1f, r.glowAlpha());
            if (g > 0.02f) {
                int inner = LyricLayout.withAlpha(r.glow() | 0xFF000000, Math.min(1f, g * 0.5f));
                int outer = LyricLayout.withAlpha(r.glow() | 0xFF000000, Math.min(1f, g * 0.2f));
                for (float[] d : DIRS) draw(c, r, d[0] * 1.6f, d[1] * 1.6f, -0.2f, outer, true);
                for (float[] d : DIRS) draw(c, r, d[0] * 0.8f, d[1] * 0.8f, -0.1f, inner, true);
            }
            draw(c, r, 0, 0, 0, r.color(), false);
        }
    }
}
