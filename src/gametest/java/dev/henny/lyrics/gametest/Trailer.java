package dev.henny.lyrics.gametest;

import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Consumer;

/**
 * Records the trailer: the demo song frame by frame (30 fps) while the camera pans and look and sky change with
 * every line. Each frame seeks the paused song to its exact time, so lyrics and music line up perfectly once
 * tools/trailer.sh puts the frames and the song together.
 * Run with: ./gradlew :fabric-1.21.11:runClientGameTest -Dvibeylyrics.trailer=true
 */
final class Trailer {
    static final int FPS = 30;
    static final long END_MS = 47_000;

    private record Scene(long from, Consumer<Minecraft> setup, float yaw0, float yaw1, float pitch0, float pitch1) {}

    private Trailer() {}

    static void record(ClientGameTestContext context, java.util.function.BiConsumer<Minecraft, Boolean> hud) {
        List<Scene> scenes = List.of(
                new Scene(0, mc -> look(LyricsConfig.Preset.LAVENDER, "sunset", false), -62, -50, -12, -15),
                new Scene(4_800, mc -> {}, -50, -40, -15, -16),
                new Scene(9_600, mc -> look(LyricsConfig.Preset.DREAMY, "nebula", true), -36, -46, -14, -18),
                new Scene(14_400, mc -> look(LyricsConfig.Preset.FIRE, "galaxy", false), -66, -56, -20, -23),
                new Scene(19_200, mc -> look(LyricsConfig.Preset.ICE, "aurora", false), -40, -50, -11, -14),
                new Scene(24_000, mc -> look(LyricsConfig.Preset.ROYAL, "saturn", false), -50, -42, -17, -19),
                new Scene(28_800, mc -> {
                    look(LyricsConfig.Preset.COMIC, "synthwave", false);
                    LyricsConfig.get().skyYaw = 0;
                }, -6, 6, -7, -9),                new Scene(33_600, mc -> look(LyricsConfig.Preset.SCRIPT, "candy", false), -40, -50, -15, -17),
                new Scene(38_400, mc -> {
                    look(LyricsConfig.Preset.LAVENDER, "moonlight", false);
                    hud.accept(mc, false);
                }, -50, -40, -15, -13),
                new Scene(43_200, mc -> {
                    look(LyricsConfig.Preset.LAVENDER, "golden", false);
                    hud.accept(mc, true);
                }, -40, -32, -13, -9)
        );
        context.runOnClient(mc -> {
            hud.accept(mc, true);
            LyricsConfig c = LyricsConfig.get();
            c.position = LyricsConfig.Position.SKY;
            c.skyFollow = false;
            c.skyYaw = -45;
            c.size = 100;
            c.reveal = LyricsConfig.Reveal.LINE;
            c.shootingStars = true;
            Playback.play(SongHolder.demo());
            Playback.pause();
        });
        int[] current = {-1};
        long frames = END_MS * FPS / 1000;
        for (int f = 0; f < frames; f++) {
            long t = f * 1000L / FPS;
            int idx = 0;
            for (int i = 0; i < scenes.size(); i++) if (scenes.get(i).from() <= t) idx = i;
            Scene s = scenes.get(idx);
            long sceneEnd = idx + 1 < scenes.size() ? scenes.get(idx + 1).from() : END_MS;
            float k = (t - s.from()) / (float) (sceneEnd - s.from());
            float ease = k * k * (3 - 2 * k);
            float yaw = s.yaw0() + (s.yaw1() - s.yaw0()) * ease;
            float pitch = s.pitch0() + (s.pitch1() - s.pitch0()) * ease;
            int scene = idx;
            boolean switched = current[0] != scene;
            context.runOnClient(mc -> {
                if (current[0] != scene) {
                    current[0] = scene;
                    s.setup().accept(mc);
                }
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                mc.player.yRotO = yaw;
                mc.player.xRotO = pitch;
                Playback.seek(t);
            });
            // a new sky texture loads on its first frame
            if (switched) context.waitTicks(3);
            context.takeScreenshot("trailer");
        }
        context.runOnClient(mc -> Playback.stop());
        System.out.println("[vibeylyrics-test] trailer: " + frames + " frames at " + FPS + " fps");
    }

    private static void look(LyricsConfig.Preset preset, String sky, boolean matchSky) {
        LyricsConfig c = LyricsConfig.get();
        c.position = LyricsConfig.Position.SKY;
        c.applyPreset(preset);
        c.sky = sky;
        c.matchSky = matchSky;
        c.skyYaw = -45;
    }

    /** Small indirection so the trailer can reuse the test's demo lookup. */
    static final class SongHolder {
        static dev.henny.lyrics.library.Song demo() {
            return dev.henny.lyrics.library.SongLibrary.find(dev.henny.lyrics.library.SongLibrary.DEMO.toLowerCase(java.util.Locale.ROOT));
        }
    }
}
