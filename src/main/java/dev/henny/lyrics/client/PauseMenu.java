package dev.henny.lyrics.client;

import dev.henny.lyrics.Compat;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;
import java.util.function.Consumer;

/** A small "♪ Lyrics" button in the top left corner of the pause menu. */
public final class PauseMenu {
    private PauseMenu() {}

    public static void decorate(Screen screen, List<? extends GuiEventListener> widgets, Consumer<AbstractWidget> add) {
        if (!(screen instanceof PauseScreen)) return;
        // Pause menu hidden (F3 + Esc): no buttons at all.
        boolean visible = false;
        for (GuiEventListener l : widgets) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && tc.getKey().equals("menu.returnToGame")) visible = true;
        }
        if (!visible) return;
        add.accept(Button.builder(Component.translatable("vibeylyrics.pause"), b -> Compat.openScreen(new LyricsScreen(screen)))
                .bounds(6, 6, 80, 20).build());
    }
}
