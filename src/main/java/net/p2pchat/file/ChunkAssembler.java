package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.protocol.PacketFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkAssembler {

    private static final int FRAME_SIZE = 128;

    private static class Frame {
        int expected;
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        boolean complete = false;
    }

    private static class FileBuffer {
        String filename;
        int totalChunks;
        Map<Integer, Frame> frames = new ConcurrentHashMap<>();
        int expectedFrameIndex = 0;
    }

    private static final Map<Integer, FileBuffer> files = new ConcurrentHashMap<>();

    // ================= FILE_INFO =================
    public static void setFileInfo(PacketHeader h, String filename) {
        files.computeIfAbsent(h.sequenceNumber, __ -> {
            FileBuffer fb = new FileBuffer();
            fb.filename = filename;
            fb.totalChunks = h.chunkLength;
            return fb;
        });
    }

    // ================= FILE_CHUNK =================
    public static synchronized void receiveChunk(PacketHeader h, byte[] payload) {

        int frameIndex = h.chunkId / FRAME_SIZE;

        FileBuffer fb = files.get(h.sequenceNumber);
        if (fb == null) return;

        // ======================================================
        // FIX 1: Alte Frames → STILL IGNORIEREN
        // ======================================================
        if (frameIndex < fb.expectedFrameIndex) {
            return;
        }

        // ======================================================
        // FIX 2: Zukünftige Frames → ignorieren (Stop-and-Wait)
        // ======================================================
        if (frameIndex > fb.expectedFrameIndex) {
            return;
        }

        int frameStart = frameIndex * FRAME_SIZE;
        int frameEnd   = Math.min(frameStart + FRAME_SIZE, fb.totalChunks);

        Frame frame = fb.frames.computeIfAbsent(frameIndex, __ -> {
            Frame f = new Frame();
            f.expected = frameEnd - frameStart;
            return f;
        });

        // ======================================================
        // FIX 3: Komplettes Frame → STILL IGNORIEREN
        // ======================================================
        if (frame.complete) {
            return;
        }

        // Chunk-Duplikat → ignorieren
        if (frame.chunks.containsKey(h.chunkId)) {
            return;
        }

        frame.chunks.put(h.chunkId, payload);

        if (frame.chunks.size() < frame.expected) {
            return;
        }

        // Vollständigkeit prüfen
        for (int i = frameStart; i < frameEnd; i++) {
            if (!frame.chunks.containsKey(i)) {
                sendNoAck(h, collectMissing(frame, frameStart, frameEnd));
                return;
            }
        }

        // ======================================================
        // Frame vollständig
        // ======================================================
        frame.complete = true;
        sendAck(h);
        fb.expectedFrameIndex++;   // EINZIGE Zustandsänderung

        if (allFramesComplete(fb)) {
            writeFile(fb);
            files.remove(h.sequenceNumber);
        }
    }

    // ================= HILFSMETHODEN =================
    private static List<Integer> collectMissing(Frame f, int start, int end) {
        List<Integer> missing = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (!f.chunks.containsKey(i)) missing.add(i);
        }
        return missing;
    }

    private static boolean allFramesComplete(FileBuffer fb) {
        int expectedFrames = (fb.totalChunks + FRAME_SIZE - 1) / FRAME_SIZE;
        if (fb.frames.size() < expectedFrames) return false;

        for (int i = 0; i < expectedFrames; i++) {
            Frame f = fb.frames.get(i);
            if (f == null || !f.complete) return false;
        }
        return true;
    }

    // ================= ACK / NO_ACK =================
    private static void sendAck(PacketHeader h) {
        var r = net.p2pchat.routing.RoutingTable.getRoute(h.sourceIp, h.sourcePort & 0xFFFF);
        if (r == null) return;

        var ack = PacketFactory.createAck(
                h.sequenceNumber,
                h.sourceIp,
                h.sourcePort & 0xFFFF
        );

        NodeContext.socket.sendPacket(
                ack,
                NodeContext.socket.socketAddressForIp(r.nextHopIp),
                r.nextHopPort
        );
    }

    private static void sendNoAck(PacketHeader h, List<Integer> missing) {
        if (missing.isEmpty()) return;

        var r = net.p2pchat.routing.RoutingTable.getRoute(h.sourceIp, h.sourcePort & 0xFFFF);
        if (r == null) return;

        var noAck = PacketFactory.createNoAck(
                h.sequenceNumber,
                h.sourceIp,
                h.sourcePort & 0xFFFF,
                missing.stream().mapToInt(i -> i).toArray()
        );

        NodeContext.socket.sendPacket(
                noAck,
                NodeContext.socket.socketAddressForIp(r.nextHopIp),
                r.nextHopPort
        );
    }

    // ================= DATEI SCHREIBEN =================
    private static void writeFile(FileBuffer fb) {
        try {
            byte[] out = new byte[
                    fb.frames.values().stream()
                            .flatMap(f -> f.chunks.values().stream())
                            .mapToInt(b -> b.length)
                            .sum()
                    ];

            int pos = 0;
            for (int chunkId = 0; chunkId < fb.totalChunks; chunkId++) {
                Frame f = fb.frames.get(chunkId / FRAME_SIZE);
                byte[] c = f.chunks.get(chunkId);
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }

            Files.write(Paths.get(fb.filename), out);

        } catch (IOException e) {
            System.err.println("[FILE WRITE ERROR] " + e.getMessage());
        }
    }
}