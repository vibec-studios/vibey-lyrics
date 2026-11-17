package dev.henny.lyrics.fabric;

import dev.henny.lyrics.Compat;
import dev.henny.lyrics.Playback;
import dev.henny.lyrics.VibeyLyrics;
import dev.henny.lyrics.client.LyricsScreen;
import dev.henny.lyrics.client.PauseMenu;
import dev.henny.lyrics.client.ui.Gfx;
import dev.henny.lyrics.library.SongLibrary;
import dev.henny.lyrics.render.HudLyrics;
import dev.henny.lyrics.render.WorldLyrics;
import dev.henny.lyrics.render.WorldSink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?}
//? if >=1.21.11 <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
//?}
//? if <1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
*///?}
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/

public final class VibeyLyricsClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static KeyMapping playKey;
    private static KeyMapping earlierKey;
    private static KeyMapping laterKey;
    private boolean smokeDone;

    @Override
    public void onInitializeClient() {
        SongLibrary.rescan();

        openKey = Compat.key("key.vibeylyrics.open", InputConstants.KEY_K);
        playKey = Compat.key("key.vibeylyrics.play", InputConstants.UNKNOWN.getValue());
        earlierKey = Compat.key("key.vibeylyrics.earlier", InputConstants.UNKNOWN.getValue());
        laterKey = Compat.key("key.vibeylyrics.later", InputConstants.UNKNOWN.getValue());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Playback.tick(mc);
            while (openKey.consumeClick()) {
                if (Compat.screen() == null) Compat.openScreen(new LyricsScreen(null));
            }
            while (playKey.consumeClick()) Playback.toggle();
            while (earlierKey.consumeClick()) shift(-100);
            while (laterKey.consumeClick()) shift(100);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Playback.shutdown());
        if (dev.henny.lyrics.LyricsConfig.get().pcMode) Playback.setPc(true);

        // HUD overlay
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Compat.id(VibeyLyrics.MOD_ID, "lyrics"),
                (g, delta) -> HudLyrics.render(new Gfx(g, Minecraft.getInstance().font)));
        //?} else
        /*HudRenderCallback.EVENT.register((g, delta) -> HudLyrics.render(new Gfx(g, Minecraft.getInstance().font)));*/

        // World text (1.21.9/1.21.10 have no Fabric event, see mixin/LevelRendererMixin)
        //? if >=26.1 {
        /*LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> WorldLyrics.render(ctx.poseStack(), new WorldSink(ctx.submitNodeCollector())));
        *///?}
        //? if >=1.21.11 <26.1 {
        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> WorldLyrics.render(ctx.matrices(), new WorldSink(ctx.commandQueue())));
        //?}
        //? if <1.21.9 {
        /*WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
            if (ctx.consumers() != null) WorldLyrics.render(pose, new WorldSink(ctx.consumers()));
        });
        *///?}

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            //? if >=26.1 {
            /*var widgets = Screens.getWidgets(screen);
            *///?} else
            var widgets = Screens.getButtons(screen);
            PauseMenu.decorate(screen, new ArrayList<>(widgets), widgets::add);
            // A fresh game directory opens the accessibility onboarding first; both mean "started".
            boolean start = screen instanceof TitleScreen || screen.getClass().getSimpleName().contains("Onboarding");
            if (start && Boolean.getBoolean("vibeylyrics.smoke") && !smokeDone) {
                smokeDone = true;
                // Load the classes the mixins change, so a mixin that does not fit this version fails right here.
                int loaded = 0;
                for (String c : new String[]{"net.minecraft.client.renderer.FogRenderer", "net.minecraft.client.renderer.fog.FogRenderer",
                        "net.minecraft.client.renderer.SkyRenderer", "net.minecraft.client.renderer.CloudRenderer",
                        "net.minecraft.client.renderer.LevelRenderer"}) {
                    try {
                        Class.forName(c);
                        loaded++;
                    } catch (ClassNotFoundException ignored) {
                    } catch (Throwable t) {
                        VibeyLyrics.LOG.error("[vibeylyrics-smoke] mixin FAILED on {}: {}", c, t.toString());
                    }
                }
                VibeyLyrics.LOG.info("[vibeylyrics-smoke] title screen reached, {} songs in the library, {} mixin targets loaded", SongLibrary.songs().size(), loaded);
                client.execute(client::stop);
            }
        });
        VibeyLyrics.LOG.info("Vibey Lyrics ready, songs folder: {}", VibeyLyrics.songsDir());
    }

    private static void shift(int ms) {
        dev.henny.lyrics.LyricsConfig cfg = dev.henny.lyrics.LyricsConfig.get();
        cfg.offsetMs += ms;
        cfg.save();
    }
}
