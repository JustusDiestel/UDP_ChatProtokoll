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
    public static void receiveChunk(PacketHeader h, byte[] payload) {

        int frameIndex = h.chunkId / FRAME_SIZE;

        System.out.println(
                "[RECV CHUNK] seq=" + h.sequenceNumber +
                        " chunkId=" + h.chunkId +
                        " frameIndex=" + frameIndex +
                        " payloadLen=" + payload.length
        );

        FileBuffer fb = files.get(h.sequenceNumber);
        if (fb == null) return;

        int frameStart = frameIndex * FRAME_SIZE;
        int frameEnd = Math.min(frameStart + FRAME_SIZE, fb.totalChunks);

        Frame frame = fb.frames.computeIfAbsent(frameIndex, __ -> {
            Frame f = new Frame();
            f.expected = frameEnd - frameStart;
            return f;
        });

// ===== FIX 1: Frame-Duplikate =====
        if (frame.complete) {
            sendAck(h);
            return;
        }

// ===== FIX 2: Chunk-Duplikate =====
        if (frame.chunks.containsKey(h.chunkId)) {
            return;
        }

        frame.chunks.put(h.chunkId, payload);

        System.out.println(
                "[FRAME STATUS] seq=" + h.sequenceNumber +
                        " frame=" + frameIndex +
                        " have=" + frame.chunks.size() +
                        "/" + frame.expected
        );

        // Noch nicht voll
        if (frame.chunks.size() < frame.expected)
            return;

        // Fehlende Chunks prüfen
        List<Integer> missing = new ArrayList<>();
        for (int i = frameStart; i < frameEnd; i++) {
            if (!frame.chunks.containsKey(i))
                missing.add(i);
        }

        if (!missing.isEmpty()) {

            System.out.println(
                    "[FRAME INCOMPLETE] seq=" + h.sequenceNumber +
                            " frame=" + frameIndex +
                            " missing=" + missing.size()
            );

            sendNoAck(h, missing);
            return;
        }

        // ===== FRAME IST VOLLSTÄNDIG =====
        frame.complete = true;

        System.out.println(
                "[FRAME COMPLETE] seq=" + h.sequenceNumber +
                        " frame=" + frameIndex
        );

        sendAck(h);

        boolean all = allFramesComplete(fb);

        System.out.println(
                "[FILE CHECK] seq=" + h.sequenceNumber +
                        " allFramesComplete=" + all +
                        " framesHave=" + fb.frames.size()
        );

        if (all) {
            writeFile(fb);
            files.remove(h.sequenceNumber); // aufräumen
        }
    }

    // ================= VOLLSTÄNDIGKEIT =================
    private static boolean allFramesComplete(FileBuffer fb) {

        for (int chunkId = 0; chunkId < fb.totalChunks; chunkId++) {
            int frameIndex = chunkId / FRAME_SIZE;
            Frame f = fb.frames.get(frameIndex);

            if (f == null || !f.chunks.containsKey(chunkId)) {

                System.out.println("[MISSING] seq=" + /*seq*/ " chunkId=" + chunkId
                        + " frameHave=" + (f == null ? 0 : f.chunks.size()) + "/" + (f == null ? -1 : f.expected));
                return false;
            }
        }
        return true;
    }

    // ================= ACK / NO_ACK =================
    private static void sendAck(PacketHeader h) {
        var r = net.p2pchat.routing.RoutingTable.getRoute(h.sourceIp, h.sourcePort & 0xFFFF);
        if (r == null) return;

        PacketFactory.createAck(
                h.sequenceNumber,
                h.sourceIp,
                h.sourcePort & 0xFFFF
        );

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
}