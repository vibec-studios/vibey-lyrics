#!/usr/bin/env bash
# Builds docs/trailer.mp4 (+ .webm) from the frames the client test records in trailer mode:
#   ./gradlew :fabric-1.21.11:runClientGameTest -Dvibeylyrics.trailer=true
#   tools/trailer.sh
# Gameplay with the demo song, a caption per scene, the logo in the corner and an end card. Needs ffmpeg.
set -euo pipefail
cd "$(dirname "$0")/.."
SHOTS=versions/fabric-1.21.11/build/run/clientGameTest/screenshots
FONT_DIR=src/main/resources/assets/vibeylyrics/font
WORK=build/trailer
mkdir -p "$WORK" docs
cp "$FONT_DIR/bungee.ttf" "$WORK/bungee.ttf"
cp assets/icon-512.png "$WORK/logo.png"
cp src/main/resources/assets/vibeylyrics/demo/pixel_skies.ogg "$WORK/song.ogg"

# 1) gameplay frames -> video (30 fps)
ffmpeg -hide_banner -loglevel error -y -framerate 30 -i "$SHOTS/%04d_trailer.png" \
  -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p "$WORK/gameplay.mp4"
GAME_LEN=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$WORK/gameplay.mp4")

# 2) captions + small logo in the corner
cap() { # text from to
  printf "drawtext=fontfile=bungee.ttf:text='%s':fontsize=32:fontcolor=white:x=48:y=44:box=1:boxcolor=black@0.45:boxborderw=16:alpha='if(lt(t,%s),0,if(lt(t,%s+0.35),(t-%s)/0.35,if(lt(t,%s-0.35),1,if(lt(t,%s),(%s-t)/0.35,0))))'" \
    "$1" "$2" "$2" "$2" "$3" "$3" "$3"
}
CAPS="$(cap 'Vibey Lyrics - lyrics mod for Minecraft' 0.6 4.5),$(cap 'Huge in the sky' 5.2 9.4),$(cap '8 looks - 8 fonts' 10.0 14.2),$(cap 'Floating in front of you' 14.8 19.0),$(cap 'Pinned in the world' 19.6 23.8),$(cap 'Karaoke word by word' 24.4 28.6),$(cap 'Bounce - glitch - rainbow' 29.2 33.4),$(cap 'Follows Spotify and your browser' 34.0 38.2),$(cap 'Or right on your HUD' 38.8 43.0)"
(cd "$WORK" && ffmpeg -hide_banner -loglevel error -y -i gameplay.mp4 -i logo.png -filter_complex \
  "[1:v]scale=72:72:flags=neighbor[logo];[0:v][logo]overlay=x=W-w-36:y=30[v0];[v0]${CAPS}[v]" \
  -map "[v]" -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p captioned.mp4)

# 3) end card: last frame blurred, logo, name, versions (4 s)
ffmpeg -hide_banner -loglevel error -y -sseof -0.1 -i "$WORK/gameplay.mp4" -frames:v 1 "$WORK/last.png"
(cd "$WORK" && ffmpeg -hide_banner -loglevel error -y -loop 1 -t 4 -i last.png -loop 1 -t 4 -i logo.png -filter_complex \
  "[0:v]gblur=sigma=18,eq=brightness=-0.18[bg];[1:v]scale=200:200:flags=neighbor[logo];[bg][logo]overlay=x=(W-w)/2:y=150[a];\
[a]drawtext=fontfile=bungee.ttf:text='VIBEY LYRICS':fontsize=72:fontcolor=white:x=(w-text_w)/2:y=380:shadowcolor=0xD946EF:shadowx=0:shadowy=4,\
drawtext=fontfile=bungee.ttf:text='Free mod for Fabric - Minecraft 1.21 to 26.3':fontsize=28:fontcolor=0xF0E6FF:x=(w-text_w)/2:y=480,\
drawtext=fontfile=bungee.ttf:text='vibec-studios.github.io':fontsize=26:fontcolor=0xE879F9:x=(w-text_w)/2:y=530,\
fade=t=in:st=0:d=0.6,format=yuv420p[v]" -map "[v]" -r 30 -c:v libx264 -preset slow -crf 18 endcard.mp4)

# 4) glue together, song underneath (fades out at the end)
TOTAL=$(python -c "print(round($GAME_LEN + 4, 2))")
(cd "$WORK" && ffmpeg -hide_banner -loglevel error -y -i captioned.mp4 -i endcard.mp4 -i song.ogg -filter_complex \
  "[0:v][1:v]concat=n=2:v=1:a=0[v];[2:a]apad,atrim=0:${TOTAL},afade=t=out:st=$(python -c "print($TOTAL-2.5)"):d=2.5[a]" \
  -map "[v]" -map "[a]" -c:v libx264 -preset slow -crf 20 -pix_fmt yuv420p -c:a aac -b:a 160k -movflags +faststart trailer.mp4)
cp "$WORK/trailer.mp4" docs/trailer.mp4
ffmpeg -hide_banner -loglevel error -y -i docs/trailer.mp4 -c:v libvpx-vp9 -b:v 0 -crf 34 -row-mt 1 -c:a libopus -b:a 128k docs/trailer.webm
ffmpeg -hide_banner -loglevel error -y -ss 26 -i docs/trailer.mp4 -frames:v 1 docs/trailer-poster.jpg
ls -la docs/trailer.*
