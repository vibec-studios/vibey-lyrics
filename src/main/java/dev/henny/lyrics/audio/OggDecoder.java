package dev.henny.lyrics.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ogg Vorbis through stb_vorbis, which ships with Minecraft (LWJGL). */
final class OggDecoder implements Decoder {
    private final ByteBuffer data;
    private final long handle;
    private final int channels;
    private final int sampleRate;
    private final long length;
    private ShortBuffer out;

    OggDecoder(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, err, null);
            if (handle == 0) {
                MemoryUtil.memFree(data);
                throw new IOException("Not a valid Ogg Vorbis file (stb_vorbis error " + err.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            channels = info.channels();
            sampleRate = info.sample_rate();
        }
        length = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public long lengthFrames() {
        return length;
    }

    @Override
    public int read(short[] buf) {
        if (out == null || out.capacity() < buf.length) {
            if (out != null) MemoryUtil.memFree(out);
            out = MemoryUtil.memAllocShort(buf.length);
        }
        out.clear();
        out.limit(buf.length - buf.length % channels);
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, out);
        if (frames <= 0) return -1;
        out.position(0);
        out.get(buf, 0, frames * channels);
        return frames;
    }

    @Override
    public void seek(long frame) {
        STBVorbis.stb_vorbis_seek(handle, (int) Math.max(0, Math.min(frame, Integer.MAX_VALUE)));
    }

    @Override
    public void close() {
        STBVorbis.stb_vorbis_close(handle);
        MemoryUtil.memFree(data);
        if (out != null) MemoryUtil.memFree(out);
        out = null;
    }
}
