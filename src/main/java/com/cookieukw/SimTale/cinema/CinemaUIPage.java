package com.cookieukw.SimTale.cinema;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CinemaUIPage extends InteractiveCustomUIPage<String> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
    private static final int PIXEL_SIZE = 4; // Each video pixel is a 4x4 UI Group
    
    // We render a 240x120 video (scaled by 4 = 960x480 canvas size on screen)
    private static final int VIDEO_WIDTH = 240;
    private static final int VIDEO_HEIGHT = 120;
    private static final int FPS = 10;

    private final Player player;
    private VideoDecoder decoder;
    private ScheduledFuture<?> renderTask;
    private final BlockingQueue<int[][]> frameQueue = new LinkedBlockingQueue<>(10);
    private long nextFrameNanos = -1;
    private final long frameNanos = TimeUnit.SECONDS.toNanos(1) / FPS;

    public CinemaUIPage(@Nonnull PlayerRef playerRefComp, Player player) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, null);
        this.player = player;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Cinema/CinemaPlayer.ui");

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PlayButton", new EventData().append("action", "play"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#StopButton", new EventData().append("action", "stop"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("action", "close"), false);
        
        // Listen to URL input changes
        eventBuilder.addEventBinding(CustomUIEventBindingType.TextChanged, "#UrlInput", new EventData().append("action", "url_changed"), true);
    }

    private String currentUrl = "";

    @Override
    public void handleDataEvent(Ref<EntityStore> storeRef, Store<EntityStore> store, String eventData) {
        if (eventData == null) return;

        // Parse extremely simple JSON manually for speed, or just string matching
        if (eventData.contains("\"action\":\"url_changed\"")) {
            // Very naive extraction, usually handled via gson
            int valStart = eventData.indexOf("\"value\":\"") + 9;
            int valEnd = eventData.indexOf("\"", valStart);
            if (valStart > 8 && valEnd > valStart) {
                currentUrl = eventData.substring(valStart, valEnd);
            }
        } else if (eventData.contains("play")) {
            if (currentUrl.isEmpty()) {
                getPlayerRef().sendMessage(Message.raw("[Cinema] Por favor insira a URL do MP4!"));
                return;
            }
            startPlayback(currentUrl);
        } else if (eventData.contains("stop")) {
            stopPlayback();
        } else if (eventData.contains("close")) {
            stopPlayback();
            player.getPageManager().setPage(storeRef, store, Page.None);
        }
    }

    private void startPlayback(String url) {
        stopPlayback();
        LOGGER.atInfo().log("[Cinema] Iniciando playback: " + url);
        
        frameQueue.clear();
        nextFrameNanos = -1;

        decoder = new VideoDecoder(url, VIDEO_WIDTH, VIDEO_HEIGHT, FPS, frame -> {
            // If queue is full, drop frames to keep up
            if (frameQueue.remainingCapacity() == 0) {
                frameQueue.poll();
            }
            frameQueue.offer(frame);
        }, this::stopPlayback);

        decoder.start();

        renderTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::tickRender, 0, 1000 / FPS, TimeUnit.MILLISECONDS);
    }

    private void stopPlayback() {
        if (decoder != null) {
            decoder.stop();
            decoder = null;
        }
        if (renderTask != null) {
            renderTask.cancel(false);
            renderTask = null;
        }
        
        // Clear canvas
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.clear("#VideoCanvas");
        update(false, cmd);
    }

    private void tickRender() {
        long now = System.nanoTime();
        if (nextFrameNanos < 0) nextFrameNanos = now;
        if (now < nextFrameNanos) return;

        nextFrameNanos += frameNanos;
        if (nextFrameNanos < now) nextFrameNanos = now + frameNanos; // catch up

        int[][] frame = frameQueue.poll();
        if (frame != null) {
            renderFrameToUI(frame);
        }
    }

    private void renderFrameToUI(int[][] frame) {
        int vh = frame.length;
        int vw = vh > 0 ? frame[0].length : 0;
        
        // Build the RLE document
        StringBuilder doc = new StringBuilder(vw * vh * 40); // Estimate capacity
        
        for (int y = 0; y < vh; y++) {
            int screenY = y * PIXEL_SIZE;
            int x = 0;
            while (x < vw) {
                int color = frame[y][x];
                int runEnd;
                // Run length encoding: find how many adjacent pixels share this color
                for (runEnd = x + 1; runEnd < vw && frame[y][runEnd] == color; runEnd++) {
                    // empty body
                }
                
                int left = x * PIXEL_SIZE;
                int width = (runEnd - x) * PIXEL_SIZE;
                
                doc.append("Group { Anchor: (Left: ").append(left)
                   .append(", Top: ").append(screenY)
                   .append(", Width: ").append(width)
                   .append(", Height: ").append(PIXEL_SIZE)
                   .append("); Background: #");
                appendHex6(doc, color);
                doc.append("; }\n");
                
                x = runEnd;
            }
        }

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.clear("#VideoCanvas");
        cmd.appendInline("#VideoCanvas", doc.toString());
        update(false, cmd);
    }

    private static void appendHex6(StringBuilder sb, int rgb) {
        sb.append(HEX_CHARS[(rgb >> 20) & 0xF]);
        sb.append(HEX_CHARS[(rgb >> 16) & 0xF]);
        sb.append(HEX_CHARS[(rgb >> 12) & 0xF]);
        sb.append(HEX_CHARS[(rgb >> 8) & 0xF]);
        sb.append(HEX_CHARS[(rgb >> 4) & 0xF]);
        sb.append(HEX_CHARS[rgb & 0xF]);
    }
}
