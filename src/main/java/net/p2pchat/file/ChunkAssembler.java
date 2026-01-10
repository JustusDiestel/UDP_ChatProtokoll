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
        boolean complete = false; // nur für Logging
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
        if (files.containsKey(h.sequenceNumber)) {
            // schon bekannt -> ignorieren (optional log)
            return;
        }
        FileBuffer fb = new FileBuffer();
        fb.filename = filename;
        fb.totalChunks = h.chunkLength;
        files.put(h.sequenceNumber, fb);

        System.out.println(
                "[FILE_INFO] seq=" + h.sequenceNumber +
                        " filename=" + filename +
                        " totalChunks=" + h.chunkLength
        );
    }

    // ================= FILE_CHUNK =================
    public static synchronized void receiveChunk(PacketHeader h, byte[] payload) {
        // ⚠️ WICHTIG: synchronized für Thread-Safety!

        int frameIndex = h.chunkId / FRAME_SIZE;

        System.out.println(
                "[RECV CHUNK] seq=" + h.sequenceNumber +
                        " chunkId=" + h.chunkId +
                        " frameIndex=" + frameIndex +
                        " payloadLen=" + payload.length
        );

        FileBuffer fb = files.get(h.sequenceNumber);
        if (fb == null) {
            System.err.println(
                    "[ERROR] Chunk ohne FILE_INFO: seq=" + h.sequenceNumber +
                            " chunkId=" + h.chunkId
            );
            return;
        }
        // ===== STOP-AND-WAIT FILTER =====
        if (frameIndex < fb.expectedFrameIndex) {
            sendAck(h);
            logAckAndExpectation(h, fb, "DUPLICATE_OLD_FRAME");
            return;
        }
        if (frameIndex > fb.expectedFrameIndex) {
            // zukünftiges Frame → ignorieren
            return;
        }

        int frameStart = frameIndex * FRAME_SIZE;
        int frameEnd = Math.min(frameStart + FRAME_SIZE, fb.totalChunks);

        Frame frame = fb.frames.computeIfAbsent(frameIndex, __ -> {
            Frame f = new Frame();
            f.expected = frameEnd - frameStart;
            return f;
        });

        // ===== FIX 1: Frame bereits komplett → sofort ACK =====
        if (frame.complete) {
            System.out.println(
                    "[DUPLICATE FRAME] seq=" + h.sequenceNumber +
                            " frame=" + frameIndex + " → resending ACK"
            );
            sendAck(h);
            logAckAndExpectation(h, fb, "DUPLICATE_COMPLETE_FRAME");
            return;
        }

        // ===== FIX 2: Chunk-Duplikat → stillschweigend ignorieren =====
        if (frame.chunks.containsKey(h.chunkId)) {
            System.out.println(
                    "[DUPLICATE CHUNK] seq=" + h.sequenceNumber +
                            " chunkId=" + h.chunkId + " → ignored"
            );
            return;
        }

        // Chunk speichern
        frame.chunks.put(h.chunkId, payload);

        System.out.println(
                "[FRAME STATUS] seq=" + h.sequenceNumber +
                        " frame=" + frameIndex +
                        " have=" + frame.chunks.size() +
                        "/" + frame.expected
        );

        // ⚠️ WICHTIG: Warten bis wir genug Chunks haben
        if (frame.chunks.size() < frame.expected) {
            return;
        }

        // ===== Frame ist voll → Vollständigkeit prüfen =====
        List<Integer> missing = new ArrayList<>();
        for (int i = frameStart; i < frameEnd; i++) {
            if (!frame.chunks.containsKey(i)) {
                missing.add(i);
            }
        }

        if (!missing.isEmpty()) {
            // Frame unvollständig → NO_ACK senden
            System.out.println(
                    "[FRAME INCOMPLETE] seq=" + h.sequenceNumber +
                            " frame=" + frameIndex +
                            " missing=" + missing.size() + " chunks: " + missing
            );

            sendNoAck(h, missing);
            return;
        }

        // ===== Frame ist VOLLSTÄNDIG =====
        // ⚠️ JETZT ERST complete setzen (nach allen Checks!)
        frame.complete = true;

        System.out.println(
                "[FRAME COMPLETE] seq=" + h.sequenceNumber +
                        " frame=" + frameIndex
        );

        sendAck(h);
        fb.expectedFrameIndex++;   // ✅ EINZIGE Mutation

        logAckAndExpectation(h, fb, "FRAME_COMPLETE");

        // ===== Prüfen ob ALLE Frames fertig sind =====
        boolean allComplete = allFramesComplete(fb);

        System.out.println(
                "[FILE CHECK] seq=" + h.sequenceNumber +
                        " allFramesComplete=" + allComplete +
                        " framesHave=" + fb.frames.size() +
                        " totalChunks=" + fb.totalChunks
        );

        if (allComplete) {
            System.out.println(
                    "[FILE COMPLETE] seq=" + h.sequenceNumber +
                            " → writing to disk"
            );
            writeFile(fb);
            files.remove(h.sequenceNumber); // aufräumen
        }
    }

    // ================= VOLLSTÄNDIGKEIT =================
    private static boolean allFramesComplete(FileBuffer fb) {

        // Berechne erwartete Anzahl Frames
        int expectedFrames = (fb.totalChunks + FRAME_SIZE - 1) / FRAME_SIZE;

        System.out.println(
                "[FILE CHECK DETAIL] totalChunks=" + fb.totalChunks +
                        " expectedFrames=" + expectedFrames +
                        " currentFrames=" + fb.frames.size()
        );

        // Prüfe, ob wir alle Frames haben
        if (fb.frames.size() < expectedFrames) {
            System.out.println(
                    "[MISSING FRAMES] have=" + fb.frames.size() +
                            " need=" + expectedFrames
            );
            return false;
        }

        // Prüfe, ob jeder Frame komplett ist
        for (int frameIdx = 0; frameIdx < expectedFrames; frameIdx++) {
            Frame f = fb.frames.get(frameIdx);

            if (f == null) {
                System.out.println("[MISSING FRAME] frameIndex=" + frameIdx);
                return false;
            }

            if (!f.complete) {
                System.out.println(
                        "[INCOMPLETE FRAME] frameIndex=" + frameIdx +
                                " have=" + f.chunks.size() + "/" + f.expected
                );
                return false;
            }
        }

        System.out.println("[ALL FRAMES COMPLETE] ✓");
        return true;
    }

    // ================= ACK / NO_ACK =================
    private static void sendAck(PacketHeader h) {
        var r = net.p2pchat.routing.RoutingTable.getRoute(h.sourceIp, h.sourcePort & 0xFFFF);
        if (r == null) {
            System.err.println(
                    "[ACK ERROR] No route to sender: " +
                            h.sourceIp + ":" + (h.sourcePort & 0xFFFF)
            );
            return;
        }

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

        System.out.println(
                "[ACK SENT] seq=" + h.sequenceNumber +
                        " to=" + (h.sourceIp) + ":" + (h.sourcePort & 0xFFFF) +
                        " via=" + r.nextHopIp + ":" + r.nextHopPort
        );
    }

    private static void sendNoAck(PacketHeader h, List<Integer> missing) {
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

            System.out.println(
                    "[FILE WRITTEN] filename=" + fb.filename +
                            " bytes=" + out.length
            );

        } catch (IOException e) {
            System.err.println("[FILE WRITE ERROR] " + e.getMessage());
        }
    }

    private static void logAckAndExpectation(PacketHeader h, FileBuffer fb, String reason) {
        System.out.println(
                "[ACK SENT] seq=" + h.sequenceNumber +
                        " reason=" + reason +
                        " nextExpectedFrame=" + fb.expectedFrameIndex
        );
    }
}