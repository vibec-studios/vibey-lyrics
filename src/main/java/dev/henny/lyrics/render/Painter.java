package dev.henny.lyrics.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Draws a {@link LyricLayout.Frame}: caption boxes, then per word a soft glow (two rings of copies) and the word. */
public abstract class Painter {
    private static final float[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.71f, 0.71f}, {-0.71f, 0.71f}, {0.71f, -0.71f}, {-0.71f, -0.71f}
    };

    protected final Font font;

    protected Painter(Font font) {
        this.font = font;
    }

    /** Draws {@code c} with its top left corner at (x, y), scaled. {@code back} = glow layer behind the text. */
    protected abstract void text(Component c, float x, float y, float scale, int argb, boolean back);

    protected abstract void box(float x1, float y1, float x2, float y2, int argb);

    public void paint(LyricLayout.Frame frame) {
        for (LyricLayout.Box b : frame.boxes()) box(b.x1(), b.y1(), b.x2(), b.y2(), b.color());
        for (LyricLayout.Run r : frame.runs()) {
            float w = font.width(r.text()) * r.scale();
            float x = r.x() - w / 2f;
            float y = r.y();
            float g = Math.min(1f, r.glowAlpha());
            if (g > 0.02f) {
                int inner = LyricLayout.withAlpha(r.glow() | 0xFF000000, Math.min(1f, g * 0.5f));
                int outer = LyricLayout.withAlpha(r.glow() | 0xFF000000, Math.min(1f, g * 0.2f));
                float s = r.scale();
                for (float[] d : DIRS) text(r.text(), x + d[0] * 1.6f * s, y + d[1] * 1.6f * s, s, outer, true);
                for (float[] d : DIRS) text(r.text(), x + d[0] * 0.8f * s, y + d[1] * 0.8f * s, s, inner, true);
            }
            text(r.text(), x, y, r.scale(), r.color(), false);
        }
    }
}
