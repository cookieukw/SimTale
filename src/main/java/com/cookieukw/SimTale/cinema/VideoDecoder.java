package com.cookieukw.SimTale.cinema;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Decodes an MP4 URL using ffmpeg and passes raw RGB frames to a callback.
 * Runs in its own thread to avoid blocking the server.
 */
public class VideoDecoder {
    
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BYTES_PER_PIXEL = 3;

    private final String url;
    private final int width;
    private final int height;
    private final int fps;
    private final Consumer<int[][]> onFrame;
    private final Runnable onComplete;
    
    private volatile Thread thread;
    private volatile Process ffmpegProcess;

    public VideoDecoder(String url, int width, int height, int fps, Consumer<int[][]> onFrame, Runnable onComplete) {
        this.url = url;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.onFrame = onFrame;
        this.onComplete = onComplete;
    }

    public void start() {
        thread = new Thread(this::decode, "Cinema-VideoDecoder");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (thread != null) thread.interrupt();
        if (ffmpegProcess != null) ffmpegProcess.destroyForcibly();
    }

    private void decode() {
        try {
            LOGGER.atInfo().log("[Cinema] Iniciando ffmpeg para: " + url);
            
            ffmpegProcess = new ProcessBuilder(
                "ffmpeg",
                "-re", "-i", url,
                "-vf", "scale=" + width + ":" + height,
                "-pix_fmt", "rgb24",
                "-r", String.valueOf(fps),
                "-f", "rawvideo", "pipe:1"
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start();

            InputStream raw = ffmpegProcess.getInputStream();
            byte[] buf = new byte[width * height * BYTES_PER_PIXEL];

            while (!Thread.currentThread().isInterrupted()) {
                int read = raw.readNBytes(buf, 0, buf.length);
                if (read < buf.length) {
                    LOGGER.atInfo().log("[Cinema] Fim do stream de video.");
                    break;
                }
                onFrame.accept(packFrame(buf, width, height));
            }
            
            if (onComplete != null && !Thread.currentThread().isInterrupted()) {
                onComplete.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.atSevere().log("[Cinema] Erro no decodificador de video: " + e.getMessage());
        } finally {
            if (ffmpegProcess != null) ffmpegProcess.destroyForcibly();
        }
    }

    private static int[][] packFrame(byte[] buf, int vw, int vh) {
        int[][] frame = new int[vh][vw];
        for (int y = 0; y < vh; y++) {
            for (int x = 0; x < vw; x++) {
                int off = (y * vw + x) * BYTES_PER_PIXEL;
                frame[y][x] = ((buf[off] & 0xFF) << 16) | ((buf[off + 1] & 0xFF) << 8) | (buf[off + 2] & 0xFF);
            }
        }
        return frame;
    }
}
