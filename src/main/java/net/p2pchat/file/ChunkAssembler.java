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
    }

    private static class FileBuffer {
        String filename;
        int totalChunks;
        Map<Integer, Frame> frames = new ConcurrentHashMap<>();
    }

    private static final Map<Integer, FileBuffer> files = new ConcurrentHashMap<>();

    // ================= FILE_INFO =================
    public static void setFileInfo(PacketHeader h, String filename) {
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
        System.out.println(
                "[RECV CHUNK] seq=" + h.sequenceNumber +
                        " chunkId=" + h.chunkId +
                        " frameIndex=" + (h.chunkId / FRAME_SIZE) +
                        " payloadLen=" + payload.length
        );

        FileBuffer fb = files.get(h.sequenceNumber);
        if (fb == null) return;

        int frameIndex = h.chunkId / FRAME_SIZE;
        int frameStart = frameIndex * FRAME_SIZE;
        int frameEnd = Math.min(frameStart + FRAME_SIZE, fb.totalChunks);

        Frame frame = fb.frames.computeIfAbsent(frameIndex, __ -> {
            Frame f = new Frame();
            f.expected = frameEnd - frameStart;
            return f;
        });

        frame.chunks.put(h.chunkId, payload);
        System.out.println(
                "[FRAME STATUS] seq=" + h.sequenceNumber +
                        " frame=" + frameIndex +
                        " have=" + frame.chunks.size() +
                        "/" + frame.expected
        );

        if (frame.chunks.size() < frame.expected) return;

        List<Integer> missing = new ArrayList<>();
        for (int i = frameStart; i < frameEnd; i++) {
            if (!frame.chunks.containsKey(i))
                missing.add(i);
        }

        if (missing.isEmpty()) {

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

            if (all)
                writeFile(fb);

        } else {

            System.out.println(
                    "[FRAME INCOMPLETE] seq=" + h.sequenceNumber +
                            " frame=" + frameIndex +
                            " missing=" + missing.size()
            );

            sendNoAck(h, missing);
        }
    }

    private static boolean allFramesComplete(FileBuffer fb) {
        int frames = (int) Math.ceil(fb.totalChunks / (double) FRAME_SIZE);
        for (int i = 0; i < frames; i++) {
            Frame f = fb.frames.get(i);
            if (f == null || f.chunks.size() != f.expected)
                return false;
        }
        return true;
    }

    private static void sendAck(PacketHeader h) {
        NodeContext.socket.sendPacket(
                PacketFactory.createAck(h.sequenceNumber, h.sourceIp, h.sourcePort & 0xFFFF),
                NodeContext.socket.socketAddressForIp(h.sourceIp),
                h.sourcePort & 0xFFFF
        );
    }

    private static void sendNoAck(PacketHeader h, List<Integer> missing) {
        NodeContext.socket.sendPacket(
                PacketFactory.createNoAck(
                        h.sequenceNumber,
                        h.sourceIp,
                        h.sourcePort & 0xFFFF,
                        missing.stream().mapToInt(i -> i).toArray()
                ),
                NodeContext.socket.socketAddressForIp(h.sourceIp),
                h.sourcePort & 0xFFFF
        );
    }

    private static void writeFile(FileBuffer fb) {
        try {
            byte[] out = new byte[fb.frames.values().stream()
                    .flatMap(f -> f.chunks.values().stream())
                    .mapToInt(b -> b.length)
                    .sum()];

            int pos = 0;
            for (int i = 0; i < fb.totalChunks; i++) {
                Frame f = fb.frames.get(i / FRAME_SIZE);
                byte[] c = f.chunks.get(i);
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }

            Files.write(Paths.get(fb.filename), out);
        } catch (IOException ignored) {}
    }
}