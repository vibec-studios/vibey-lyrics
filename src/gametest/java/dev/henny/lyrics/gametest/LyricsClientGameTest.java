package dev.henny.lyrics.gametest;

import dev.henny.lyrics.LyricsConfig;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.client.AdvancedScreen;
import dev.henny.lyrics.client.LyricsScreen;
import dev.henny.lyrics.client.SearchScreen;
import dev.henny.lyrics.library.Song;
import dev.henny.lyrics.library.SongLibrary;
import dev.henny.lyrics.lyrics.Lyrics;
import dev.henny.lyrics.pc.NowPlaying;
import dev.henny.lyrics.pc.PcLyrics;
import dev.henny.lyrics.render.WorldLyrics;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Runs the real game: checks the demo song and its word timing, then plays it in a fresh world and takes a
 * screenshot of every position and the menus. Fails if the world render hook never drew anything.
 * Screenshots land in versions/<version>/run/clientGameTest/screenshots (or build/run/... depending on Loom).
 */
public final class LyricsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            SongLibrary.rescan();
            Song demo = demo();
            Lyrics lyrics;
            try {
                lyrics = SongLibrary.loadLyrics(demo);
            } catch (Exception e) {
                throw new AssertionError("Demo lyrics unreadable", e);
            }
            long sung = lyrics.lines().stream().filter(l -> !l.isBreak()).count();
            if (!lyrics.wordSync() || sung != 8) throw new AssertionError("Demo lyrics parsed wrong: " + sung + " lines, wordSync=" + lyrics.wordSync());
            Lyrics.Line chorus = lyrics.lines().get(lyrics.lineAt(25_300));
            if (!chorus.text().equals("Pixel skies, pixel skies")) throw new AssertionError("Wrong line at 25.3 s: " + chorus.text());
            System.out.println("[vibeylyrics-test] demo song ok: " + sung + " lines, word sync");
        });

        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(s -> s.setSeed("vibeylyrics"))
                .create()) {
            world.getServer().runCommand("time set 12000");
            world.getServer().runCommand("weather clear");
            context.waitTicks(100);
            // Spawn is random within a radius: stand on a small glass platform above it for a clear view.
            int[] p = context.computeOnClient(mc -> new int[]{
                    (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())});
            int top = p[1] + 14;
            world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass", p[0] - 1, top, p[2] - 1, p[0] + 1, top, p[2] + 1));
            world.getServer().runCommand(String.format("tp @a %d.5 %d %d.5", p[0], top + 1, p[2]));
            context.waitTicks(140);
            if (Boolean.getBoolean("vibeylyrics.trailer")) {
                Trailer.record(context, LyricsClientGameTest::hideHud);
                return;
            }
            if ("sky".equals(System.getProperty("vibeylyrics.only"))) {
                setup(context, mc -> Playback.play(demo()));
                skyScenes(context);
                return;
            }

            setup(context, mc -> {
                hideHud(mc, true);
                mc.player.setYRot(-45);
                mc.player.setXRot(-14);
                LyricsConfig c = LyricsConfig.get();
                c.position = LyricsConfig.Position.SKY;
                c.applyPreset(LyricsConfig.Preset.NEON);
                c.reveal = LyricsConfig.Reveal.LINE;
                c.skyFollow = false;
                c.size = 100;
                Playback.play(demo());
            });
            scene(context, 2200, "0_intro_sky");
            scene(context, 25_350, "1_sky_neon");
            long drawn = context.computeOnClient(mc -> WorldLyrics.drawnFrames);
            if (drawn <= 0) throw new AssertionError("World lyrics were never drawn: the render hook did not run");
            System.out.println("[vibeylyrics-test] world hook drew " + drawn + " frames");

            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                c.applyPreset(LyricsConfig.Preset.SUNSET);
                c.reveal = LyricsConfig.Reveal.WORD;
            });
            scene(context, 16_350, "2_sky_sunset_words");

            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                c.position = LyricsConfig.Position.FRONT;
                c.applyPreset(LyricsConfig.Preset.OCEAN);
                c.reveal = LyricsConfig.Reveal.LINE;
                mc.player.setXRot(4);
            });
            scene(context, 30_700, "3_front_ocean");

            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                mc.player.setXRot(-8);
                c.position = LyricsConfig.Position.WORLD;
                c.applyPreset(LyricsConfig.Preset.CLASSIC);
                c.box = true;
                LyricsScreen.placeHere();
                mc.player.setYRot(-70);
            });
            scene(context, 20_500, "4_world_classic");

            setup(context, mc -> {
                hideHud(mc, false);
                mc.player.setYRot(-45);
                mc.player.setXRot(-14);
                LyricsConfig c = LyricsConfig.get();
                c.position = LyricsConfig.Position.HUD;
                c.applyPreset(LyricsConfig.Preset.MINIMAL);
            });
            scene(context, 11_000, "5_hud_minimal");

            // The other looks: each brings its own font and animation.
            setup(context, mc -> {
                hideHud(mc, true);
                LyricsConfig c = LyricsConfig.get();
                c.position = LyricsConfig.Position.SKY;
                c.applyPreset(LyricsConfig.Preset.ARCADE);
            });
            scene(context, 24_090, "6_sky_arcade_glitch");
            setup(context, mc -> LyricsConfig.get().applyPreset(LyricsConfig.Preset.MARKER));
            scene(context, 29_470, "7_sky_marker_bounce");
            setup(context, mc -> LyricsConfig.get().applyPreset(LyricsConfig.Preset.RETRO));
            scene(context, 35_200, "8_sky_retro_rainbow");
            setup(context, mc -> LyricsConfig.get().applyPreset(LyricsConfig.Preset.OCEAN));
            scene(context, 39_000, "9_sky_ocean_wave");
            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                c.applyPreset(LyricsConfig.Preset.SUNSET);
                c.reveal = LyricsConfig.Reveal.LINE;
            });
            scene(context, 34_300, "10_sky_sunset_zoom");

            skyScenes(context);
            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                c.sky = "vanilla";
                c.matchSky = false;
                c.position = LyricsConfig.Position.SKY;
            });
            setup(context, mc -> hideHud(mc, false));

            setup(context, mc -> {
                LyricsConfig c = LyricsConfig.get();
                c.position = LyricsConfig.Position.SKY;
                c.applyPreset(LyricsConfig.Preset.NEON);
            });
            seek(context, 29_650);
            guiScale(context, 2);
            context.setScreen(() -> new LyricsScreen(null));
            context.waitTicks(12);
            context.takeScreenshot("11_menu");
            context.getInput().setCursorPos(300, 400);
            context.getInput().scroll(-30);
            context.waitTicks(6);
            context.takeScreenshot("11b_menu_skies");
            context.setScreen(() -> new AdvancedScreen(new LyricsScreen(null)));
            context.waitTicks(12);
            context.takeScreenshot("12_advanced");
            context.setScreen(() -> new SearchScreen(new LyricsScreen(null), null));
            context.waitTicks(12);
            context.takeScreenshot("13_search");
            context.setScreen(() -> null);
            guiScale(context, 0);
            context.runOnClient(mc -> Playback.stop());

            // Music from the PC: only checked if something actually plays on this machine during the test.
            context.runOnClient(mc -> Playback.setPc(true));
            boolean found;
            try {
                context.waitFor(mc -> NowPlaying.track() != null, 200);
                found = true;
            } catch (AssertionError | RuntimeException e) {
                found = false;
            }
            if (found) {
                String what = context.computeOnClient(mc -> NowPlaying.track().appName() + ": " + NowPlaying.track().title());
                System.out.println("[vibeylyrics-test] PC music detected (" + what + ")");
                try {
                    context.waitFor(mc -> PcLyrics.status() != PcLyrics.Status.LOADING && PcLyrics.status() != PcLyrics.Status.IDLE, 400);
                } catch (AssertionError | RuntimeException ignored) {
                }
                System.out.println("[vibeylyrics-test] PC lyrics: " + context.computeOnClient(mc -> PcLyrics.status().name()));
                setup(context, mc -> {
                    hideHud(mc, true);
                    LyricsConfig.get().applyPreset(LyricsConfig.Preset.NEON);
                });
                context.waitTicks(20);
                context.takeScreenshot("14_pc_mode");
                setup(context, mc -> hideHud(mc, false));
            } else {
                System.out.println("[vibeylyrics-test] no PC music playing, PC mode check skipped");
            }
            context.runOnClient(mc -> Playback.setPc(false));
        }
    }

    /** 1.1: words as 3D objects, custom skies, colors from the sky, the now-playing bar. */
    private static void skyScenes(ClientGameTestContext context) {
        look(context, LyricsConfig.Preset.LAVENDER, "sunset", -45, -14);
        scene(context, 16_700, "15_flight_sunset");
        scene(context, 19_650, "16_flight_flyby");
        look(context, LyricsConfig.Preset.DREAMY, "nebula", -45, -16);
        setup(context, mc -> LyricsConfig.get().matchSky = true);
        scene(context, 34_950, "17_drift_nebula");
        look(context, LyricsConfig.Preset.FIRE, "galaxy", -60, -22);
        scene(context, 30_700, "18_flight_galaxy_fire");
        look(context, LyricsConfig.Preset.ICE, "aurora", -45, -12);
        scene(context, 21_000, "19_drift_aurora_ice");
        look(context, LyricsConfig.Preset.ROYAL, "saturn", -45, -18);
        scene(context, 25_900, "20_saturn_royal");
        look(context, LyricsConfig.Preset.COMIC, "synthwave", 0, -8);
        scene(context, 29_450, "21_synthwave_comic");
        look(context, LyricsConfig.Preset.SCRIPT, "candy", -45, -16);
        scene(context, 10_700, "22_candy_script");
        look(context, LyricsConfig.Preset.LAVENDER, "moonlight", -45, -16);
        scene(context, 39_950, "23_moonlight_flight");
        look(context, LyricsConfig.Preset.FIRE, "crimson", -45, -16);
        scene(context, 6_300, "24_crimson_fire");
        look(context, LyricsConfig.Preset.LAVENDER, "golden", -45, -14);
        scene(context, 35_200, "25_golden_flight");
        look(context, LyricsConfig.Preset.DREAMY, "dreamy", -45, -14);
        scene(context, 15_900, "26_dreamy_drift");
        look(context, LyricsConfig.Preset.LAVENDER, "sunset", -45, -14);
        setup(context, mc -> hideHud(mc, false));
        scene(context, 30_400, "27_bar_flight_sunset");
        setup(context, mc -> LyricsConfig.get().position = LyricsConfig.Position.HUD);
        scene(context, 25_900, "28_hud_flight");
    }

    /** Sets the GUI scale and applies it (resizeDisplay up to 1.21.11, resizeGui in 26.x). */
    private static void guiScale(ClientGameTestContext context, int scale) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            for (String name : new String[]{"resizeDisplay", "resizeGui"}) {
                try {
                    Minecraft.class.getMethod(name).invoke(mc);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        });
        context.waitTicks(2);
    }

    /** F1 on or off: Options.hideGui up to 26.1, Hud.isHidden from 26.2 (no setter, so by reflection). */
    private static void hideHud(Minecraft mc, boolean hidden) {
        try {
            var f = mc.options.getClass().getField("hideGui");
            f.setBoolean(mc.options, hidden);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            Object hud = gui.getClass().getField("hud").get(gui);
            var f = hud.getClass().getDeclaredField("isHidden");
            f.setAccessible(true);
            f.setBoolean(hud, hidden);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Song demo() {
        Song demo = SongLibrary.find(SongLibrary.DEMO.toLowerCase(Locale.ROOT));
        if (demo == null) throw new AssertionError("Demo song missing from " + SongLibrary.songs());
        return demo;
    }

    /** Sky position with a look and a custom sky, F1 on, looking the given way. */
    private static void look(ClientGameTestContext context, LyricsConfig.Preset preset, String sky, float yaw, float pitch) {
        setup(context, mc -> {
            hideHud(mc, true);
            LyricsConfig c = LyricsConfig.get();
            c.position = LyricsConfig.Position.SKY;
            c.applyPreset(preset);
            c.reveal = LyricsConfig.Reveal.LINE;
            c.sky = sky;
            c.matchSky = false;
            c.skyYaw = yaw;
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        // let the new sky texture load and the fog settle
        context.waitTicks(10);
    }

    private static void setup(ClientGameTestContext context, Consumer<Minecraft> action) {
        context.runOnClient(action::accept);
    }

    private static void seek(ClientGameTestContext context, long ms) {
        context.runOnClient(mc -> {
            Playback.seek(ms);
            Playback.pause();
        });
        context.waitTicks(4);
    }

    /** Freezes the song at {@code ms} and takes a screenshot. */
    private static void scene(ClientGameTestContext context, long ms, String name) {
        seek(context, ms);
        context.waitTicks(6);
        context.takeScreenshot(name);
    }
}
