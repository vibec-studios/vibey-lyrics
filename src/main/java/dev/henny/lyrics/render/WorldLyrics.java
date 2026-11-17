package dev.henny.lyrics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.henny.lyrics.Compat;
import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.sky.SkyDome;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Lyrics in the world: huge and far away in the sky, floating in front of you, or pinned to a spot. The frame is a
 * flat billboard turned towards the camera; its size grows with the distance so the sky text stays readable.
 * Called from the world render hook with a pose stack whose origin is the camera position.
 */
public final class WorldLyrics {
    private static float smoothYaw = Float.NaN;
    private static float smoothPitch;
    private static long lastNanos;
    /** Frames in which world lyrics were handed to the renderer (the client test checks the hook works). */
    public static long drawnFrames;

    private WorldLyrics() {}

    public static void render(PoseStack pose, WorldSink sink) {
        SkyDome.render(pose, sink);
        LyricsConfig cfg = LyricsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (cfg.position == LyricsConfig.Position.HUD || Playback.song() == null || mc.level == null) return;
        Camera cam = Compat.camera();
        Vec3 eye = Compat.cameraPos(cam);
        float camYaw = Compat.cameraYaw(cam);
        float camPitch = Compat.cameraPitch(cam);
        smooth(camYaw, camPitch);

        float size = cfg.size / 100f;
        Vec3 target;
        float scale;
        float maxWidth;
        boolean faceCamera = true;
        float fixedYaw = 0;
        switch (cfg.position) {
            case SKY -> {
                // Far, but well inside the fog so it never fades out; scaled with the distance.
                double rd = mc.options.getEffectiveRenderDistance() * 16.0;
                double dist = Math.max(32, Math.min(96, rd * 0.35));
                float yaw = cfg.skyFollow ? smoothYaw : cfg.skyYaw;
                target = eye.add(direction(yaw, -cfg.skyHeight).scale(dist));
                scale = (float) (dist * 0.013 * size);
                maxWidth = 140;
            }
            case FRONT -> {
                double dist = cfg.frontDistance;
                target = eye.add(direction(smoothYaw, smoothPitch + 6f).scale(dist));
                scale = (float) (dist * 0.0075 * size);
                maxWidth = 150;
            }
            default -> {
                if (!cfg.anchorSet) return;
                target = new Vec3(cfg.anchorX, cfg.anchorY, cfg.anchorZ);
                scale = 0.07f * size;
                maxWidth = 220;
                faceCamera = cfg.anchorBillboard;
                fixedYaw = cfg.anchorYaw;
            }
        }
        Vec3 rel = target.subtract(eye);
        float room = (float) (rel.length() / scale);
        LyricLayout.Frame frame = LyricLayout.build(mc.font, Playback.lyrics(), Playback.song().title(),
                Playback.song().artist(), Playback.lyricTimeMs(), maxWidth, room);
        if (frame.isEmpty()) return;

        Quaternionf rot;
        if (faceCamera) {
            double h = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
            float yaw = (float) Math.atan2(-rel.x, rel.z);
            float pitch = (float) Math.atan2(-rel.y, h);
            rot = new Quaternionf().rotationYXZ((float) Math.PI - yaw, -pitch, 0f);
        } else {
            rot = new Quaternionf().rotationY((float) Math.PI - (float) Math.toRadians(fixedYaw));
        }

        pose.pushPose();
        pose.translate(rel.x, rel.y, rel.z);
        Compat.rotate(pose, rot);
        pose.scale(scale, -scale, scale);
        Font font = mc.font;
        new Painter(font) {
            @Override
            protected void draw(Component c, LyricLayout.Run r, float ox, float oy, float oz, int argb, boolean back) {
                float s = r.scale();
                pose.pushPose();
                pose.translate(r.x(), r.y() + LyricLayout.ROW * s / 2f, r.z());
                if (r.yaw() != 0f || r.roll() != 0f) {
                    Compat.rotate(pose, new Quaternionf().rotationYXZ((float) Math.toRadians(r.yaw()), 0f, (float) Math.toRadians(r.roll())));
                }
                pose.scale(s, s, s);
                // Glow copies and 3D sides sit a hair behind the text so they never cover it.
                pose.translate(-font.width(c) / 2f + ox, -LyricLayout.ROW / 2f + oy, oz);
                sink.text(font, pose, c, 0, 0, argb, 0, !back);
                pose.popPose();
            }

            @Override
            protected void box(float x1, float y1, float x2, float y2, int argb) {
                // A text background is the one rectangle every version can draw here: stretch one space.
                Component space = Component.literal(" ");
                float w = font.width(space);
                pose.pushPose();
                pose.translate(x1, y1, -0.7f);
                pose.scale((x2 - x1) / (w + 2f), (y2 - y1) / 10f, 1f);
                sink.text(font, pose, space, 1, 1, 0, argb, false);
                pose.popPose();
            }
        }.paint(frame);
        pose.popPose();
        drawnFrames++;
    }

    /** Eases the "follow view" direction so the text floats after your head instead of being glued to it. */
    private static void smooth(float yaw, float pitch) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min(0.25f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (Float.isNaN(smoothYaw)) {
            smoothYaw = yaw;
            smoothPitch = pitch;
            return;
        }
        float k = 1f - (float) Math.exp(-dt * 4.5f);
        float d = ((yaw - smoothYaw) % 360f + 540f) % 360f - 180f;
        smoothYaw += d * k;
        smoothPitch += (pitch - smoothPitch) * k;
    }

    private static Vec3 direction(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double c = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * c, -Math.sin(pitch), Math.cos(yaw) * c);
    }
}
