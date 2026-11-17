package dev.henny.lyrics;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class VibeyLyrics {
    public static final String MOD_ID = "vibeylyrics";
    public static final Logger LOG = LoggerFactory.getLogger("Vibey Lyrics");

    private VibeyLyrics() {}

    /** Songs and .lrc files: {@code .minecraft/vibey-lyrics/}. */
    public static Path songsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("vibey-lyrics");
    }

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("vibey-lyrics.json");
    }
}
