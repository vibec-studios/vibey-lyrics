<img src="assets/icon.svg" width="64" height="64" alt="">

# Vibey Lyrics

**Synced lyrics for your music, TikTok-style: huge in the sky, floating in front of you, pinned in the world or on
the HUD.**

A client mod for Fabric by Vibe Craft Studios. Play a song from your own folder, or let Vibey Lyrics follow the music
that already plays on your PC (Spotify, a browser, any player). The lyrics are fetched automatically and show up line
by line with karaoke highlighting, glow and animations.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21%E2%80%9326.3-green)
![Loader](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

[![Watch the trailer](docs/trailer-poster.jpg)](docs/trailer.mp4)

*▶ [Watch the trailer](docs/trailer.mp4) (51 s, recorded in game by the automated client test).*

## Features

- **Four positions:** *Sky* (huge, high up in one compass direction or following your view, hidden behind
  mountains like a real sky), *In front* (floats in front of you and follows your head), *In world* (pinned to a spot,
  turns towards you or stays fixed like a sign) and *HUD* (a screen caption).
- **Music from your PC:** turn it on and Vibey Lyrics follows what plays on this computer, Spotify, YouTube in a
  browser, a media player. It downloads the synced lyrics from [LRCLIB](https://lrclib.net) and keeps them in time with
  the song. Play/pause in the menu controls the player.
- **Your own songs:** `.ogg`, `.mp3` and `.wav` files in `.minecraft/vibey-lyrics/`, each with an `.lrc` file of the same
  name. No `.lrc`? *Find lyrics* searches LRCLIB and saves one. An `.lrc` without audio runs on a timer for music that
  plays somewhere else.
- **Karaoke look:** words light up the moment they are sung (word timing from enhanced LRC files, estimated otherwise),
  a soft glow, the next line as a preview, optional caption boxes, sparkles flying off every sung word.
- **Animations:** Pop, Fade, Slide, Zoom, Wave, Bounce, Typewriter, Glitch, Rainbow; line by line or word by word.
- **Fonts:** Minecraft, Unicode, Bebas Neue, Bungee, Marker, Pacifico, Arcade (Press Start 2P), Monoton.
- **Eight looks in one click** (Neon, Classic, Sunset, Ocean, Arcade, Marker, Retro, Minimal); everything else
  (font, colors, size, spacing, sync offset, sky height, volume …) under *Advanced*.
- A demo song made for this mod, *Pixel Skies*, with word-timed lyrics, so you can try everything right away.
- English and German.

| Neon in the sky | Sunset (Pacifico, zoom) |
|---|---|
| ![Neon](docs/screenshots/sky-neon.png) | ![Sunset](docs/screenshots/sky-sunset.png) |
| **Retro (Monoton, rainbow)** | **Arcade (glitch)** |
| ![Retro](docs/screenshots/sky-retro.png) | ![Arcade](docs/screenshots/sky-arcade.png) |
| **In front of you** | **Pinned in the world** |
| ![In front](docs/screenshots/front.png) | ![In world](docs/screenshots/world.png) |
| **HUD** | **Menu** |
| ![HUD](docs/screenshots/hud.png) | ![Menu](docs/screenshots/menu.png) |

*Screenshots from the automated client test (Fabric 1.21.11) with the demo song.*

## Supported versions

| Minecraft | Fabric |
|---|---|
| 1.21, 1.21.1 | ✔ |
| 1.21.2, 1.21.3 | ✔ |
| 1.21.4 | ✔ |
| 1.21.5 | ✔ |
| 1.21.6 – 1.21.8 | ✔ |
| 1.21.9, 1.21.10 | ✔ |
| 1.21.11 | ✔ |
| 26.1, 26.2, 26.3 | ✔ |

Needs [Fabric API](https://modrinth.com/mod/fabric-api). Client only.

## How to use

Press **K** (or *♪ Lyrics* in the pause menu).

- **Music from your PC:** switch on *Music from your PC* at the top of the song list and start a song in Spotify, a
  browser or a player.
  - Windows: works with everything that shows up in the Windows media controls (the volume flyout).
  - macOS: Spotify and Apple Music.
  - Linux: any MPRIS player, needs [`playerctl`](https://github.com/altdesktop/playerctl).
  - Out of sync? Shift the lyrics with −0.5s / +0.5s in the menu, *Sync offset* under *Advanced* or the
    *Lyrics earlier / later* keys (unbound by default).
- **Your own songs:** *Open folder*, drop in `Artist - Title.ogg` (or `.mp3`, `.wav`) and `Artist - Title.lrc`.

### LRC files

```
[ti:Pixel Skies]
[ar:Vibe Craft Studios]
[00:04.80]Up above the blocky hills
[00:24.00]<00:24.00>Pixel <00:24.60>skies, <00:25.20>pixel <00:25.80>skies <00:27.00>
```

Line tags (`[mm:ss.xx]`) are enough; word tags (`<mm:ss.xx>`, "enhanced LRC") make the karaoke exact. `[offset:]`
and several time tags per line work too.

## Lyrics and privacy

The mod ships **no song lyrics** apart from its own demo song. Lyrics come from your `.lrc` files or from
[LRCLIB](https://lrclib.net), a free, community-run lyrics database. Only the artist, title, album and length of
the song are sent to lrclib.net, nothing about you or your game. Downloaded lyrics are cached in
`.minecraft/vibey-lyrics/pc/`. *Advanced → Online lyrics* turns all lookups off.

On Windows the PC music is read by a small PowerShell script that ships with the mod
([`windows.ps1`](src/main/resources/assets/vibeylyrics/nowplaying/windows.ps1)); it only reads the media controls
and stops with the game.

## Building

```bash
./gradlew buildAll          # every version, jars land in build/dist
./gradlew :fabric-1.21.11:runClient
./gradlew :fabric-1.21.11:runClientGameTest   # plays the demo song in a world, screenshots of every position
tools/smoke-all.sh          # starts every version up to the title screen
tools/gametest-all.sh       # the client test on one version per render path

# trailer: record the frames in game, then cut them with ffmpeg
./gradlew :fabric-1.21.11:runClientGameTest -Dvibeylyrics.trailer=true && tools/trailer.sh
```

Requires JDK 21 and JDK 25 (Minecraft 26.x). Multi-version builds use
[Stonecutter](https://stonecutter.kikugie.dev/): one source tree, version-specific lines are marked with
`//? if >=1.21.9` comments. What differs between versions sits in `Compat.java`, `client/ui/Gfx.java`,
`client/ui/BaseScreen.java`, `render/WorldSink.java` and the client entry point:

- World text goes through `Font.drawInBatch` into a buffer source up to 1.21.8 and through the frame's
  `SubmitNodeCollector` from 1.21.9 on.
- Hooks: Fabric `WorldRenderEvents.AFTER_ENTITIES` (1.21.1–1.21.8), a small mixin (1.21.9/1.21.10, where Fabric API has
  no world render event), `WorldRenderEvents.BEFORE_ENTITIES` (1.21.11), `LevelRenderEvents.COLLECT_SUBMITS` (26.x).

`tools/DemoSong.java` synthesizes the demo song and writes its LRC, `tools/Logo.java` draws the logo.

## Settings file

`config/vibey-lyrics.json` holds all settings.

## License

[MIT](LICENSE), including the demo song *Pixel Skies*. Not an official Minecraft product. Not approved by or
associated with Mojang or Microsoft.

Bundled third-party parts: the fonts Bebas Neue, Bungee, Pacifico, Press Start 2P and Monoton
([SIL Open Font License 1.1](licenses/fonts)), Permanent Marker ([Apache 2.0](licenses/fonts/marker-LICENSE.txt)), and
[JavaMP3](https://github.com/delthas/JavaMP3) (MIT) for MP3 decoding.
