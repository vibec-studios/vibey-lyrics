package dev.henny.lyrics.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.render.WorldSink;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the custom sky: a half sphere around the camera with the sky's panorama on it, far away but inside the fog,
 * so the land in front of it covers it like the real sky. Night skies turn slowly and get shooting stars.
 * Called from the world render hook with a pose stack whose origin is the camera.
 */
public final class SkyDome {
    private static final int LON = 64;
    private static final int LAT = 18;
    /** Below the horizon the sky fades out over this many degrees into the fog (which has the horizon color). */
    private static final float FADE_DEG = 6f;

    private static SkyTheme shown;
    private static long since;

    private SkyDome() {}

    public static void render(PoseStack pose, WorldSink sink) {
        SkyTheme theme = SkyLook.active();
        if (theme == null) return;
        Minecraft mc = Minecraft.getInstance();
        double rd = mc.options.getEffectiveRenderDistance() * 16.0;
        // Just behind the sky lyrics (see WorldLyrics) and well inside the fog, which would wash it out further away.
        float radius = (float) (Math.max(32, Math.min(96, rd * 0.35)) + 28);
        long now = System.currentTimeMillis();
        if (theme != shown) {
            shown = theme;
            since = now;
        }
        // one turn in 40 minutes, counted from when the sky was picked
        float spin = theme.night ? ((now - since) % 2_400_000L) / 2_400_000f * 360f : 0f;

        pose.pushPose();
        Compat.rotate(pose, new Quaternionf().rotationY((float) Math.toRadians(spin)));
        sink.textured(pose, Compat.id(VibeyLyrics.MOD_ID, "textures/sky/" + theme.id() + ".png"), (m, vc) -> dome(m, vc, radius));
        if (theme.night && LyricsConfig.get().shootingStars) {
            sink.textured(pose, Compat.id(VibeyLyrics.MOD_ID, "textures/sky/streak.png"), (m, vc) -> shootingStars(m, vc, radius * 0.97f, now));
        }
        pose.popPose();
    }

    private static void dome(Matrix4f m, VertexConsumer vc, float r) {
        // a band below the horizon that fades out covers the vanilla sky (and sun) down there
        for (int i = 0; i < LON; i++) {
            float l0 = 360f * i / LON;
            float l1 = 360f * (i + 1) / LON;
            vertex(vc, m, r, l1, 0f);
            vertex(vc, m, r, l1, -FADE_DEG);
            vertex(vc, m, r, l0, -FADE_DEG);
            vertex(vc, m, r, l0, 0f);
        }
        for (int j = 0; j < LAT; j++) {
            float e0 = 90f * j / LAT;
            float e1 = 90f * (j + 1) / LAT;
            for (int i = 0; i < LON; i++) {
                float l0 = 360f * i / LON;
                float l1 = 360f * (i + 1) / LON;
                // Seen from inside: top left, bottom left, bottom right, top right (more longitude = further left).
                vertex(vc, m, r, l1, e1);
                vertex(vc, m, r, l1, e0);
                vertex(vc, m, r, l0, e0);
                vertex(vc, m, r, l0, e1);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float r, float lonDeg, float elevDeg) {
        double lon = Math.toRadians(lonDeg);
        double elev = Math.toRadians(elevDeg);
        float ce = (float) Math.cos(elev);
        float x = (float) (ce * Math.sin(lon)) * r;
        float y = (float) Math.sin(elev) * r;
        float z = (float) (ce * Math.cos(lon)) * r;
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, 1f + elevDeg / FADE_DEG)));
        float u = lonDeg / 360f;
        float v = Math.min(1f, 1f - elevDeg / 90f);
        vc.addVertex(m, x, y, z).setColor((alpha << 24) | 0xFFFFFF).setUv(u, v).setLight(Compat.FULL_BRIGHT);
    }

    /** A shooting star every few seconds, at a place and in a direction that come from its slot of time. */
    private static void shootingStars(Matrix4f m, VertexConsumer vc, float r, long now) {
        long slotLen = 4200;
        long slot = now / slotLen;
        for (long s = slot - 1; s <= slot; s++) {
            float delay = hash(s, 1) * 2400f;
            float life = 750f + hash(s, 2) * 500f;
            float age = now - s * slotLen - delay;
            if (age < 0 || age > life || hash(s, 3) < 0.25f) continue;
            float p = age / life;
            double lon = hash(s, 4) * Math.PI * 2;
            double elev = Math.toRadians(25 + hash(s, 5) * 40);
            Vector3f start = dir(lon, elev);
            // moves along a great circle, mostly sideways and a bit down
            double heading = Math.toRadians(-20 - hash(s, 6) * 50) * (hash(s, 7) < 0.5f ? 1 : -1);
            Vector3f east = new Vector3f(0, 1, 0).cross(start).normalize();
            Vector3f north = new Vector3f(start).cross(east).normalize();
            Vector3f along = new Vector3f(east).mul((float) Math.cos(heading)).add(new Vector3f(north).mul((float) Math.sin(heading))).normalize();
            float travel = 0.35f;
            Vector3f head = new Vector3f(start).add(new Vector3f(along).mul(travel * p)).normalize();
            Vector3f tail = new Vector3f(start).add(new Vector3f(along).mul(travel * Math.max(0f, p - 0.28f))).normalize();
            Vector3f side = new Vector3f(head).cross(along).normalize().mul(0.0045f);
            float fade = Math.min(1f, Math.min(p / 0.15f, (1f - p) / 0.3f));
            int a = Math.round(255 * fade);
            int color = (a << 24) | 0xF4F6FF;
            quad(vc, m, r, tail, head, side, color);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float r, Vector3f tail, Vector3f head, Vector3f side, int color) {
        Vector3f[] c = {
                new Vector3f(tail).add(side), new Vector3f(tail).sub(side),
                new Vector3f(head).sub(side), new Vector3f(head).add(side)
        };
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        // both windings, so it shows whichever way it turns
        for (int k = 0; k < 4; k++) put(vc, m, r, c[k], uv[k], color);
        for (int k = 3; k >= 0; k--) put(vc, m, r, c[k], uv[k], color);
    }

    private static void put(VertexConsumer vc, Matrix4f m, float r, Vector3f p, float[] uv, int color) {
        vc.addVertex(m, p.x * r, p.y * r, p.z * r).setColor(color).setUv(uv[0], uv[1]).setLight(Compat.FULL_BRIGHT);
    }

    private static Vector3f dir(double lon, double elev) {
        float ce = (float) Math.cos(elev);
        return new Vector3f((float) (ce * Math.sin(lon)), (float) Math.sin(elev), (float) (ce * Math.cos(lon)));
    }

    private static float hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }
}
