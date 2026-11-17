package dev.henny.lyrics.library;

import java.nio.file.Path;

/**
 * One entry of the song folder: an audio file and/or an .lrc file with the same name. Without audio the lyrics run
 * on a timer, for music that plays somewhere else (Spotify, a speaker, ...).
 */
public record Song(String id, String title, String artist, Path audio, Path lrc) {
    public boolean hasAudio() {
        return audio != null;
    }

    public boolean hasLyrics() {
        return lrc != null;
    }

    public String label() {
        return artist.isBlank() ? title : artist + " – " + title;
    }

    /** File name without extension, shared by the audio and .lrc file. */
    public String baseName() {
        Path p = audio != null ? audio : lrc;
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
