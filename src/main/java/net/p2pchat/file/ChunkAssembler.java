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

    private static class FileBuffer {
        int totalChunks = -1;
        String filename = "received.bin";
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    private static final Map<String, FileBuffer> files = new ConcurrentHashMap<>();

    // ============================================================
    // Eindeutige Key:
    //   sourceIp + ":" + sourcePort   (Absender bestimmt Datei!)
    // ============================================================
    private static String fileKey(PacketHeader h) {
        return h.sourceIp + ":" + (h.sourcePort & 0xFFFF);
    }

    // ============================================================
    // FILE_INFO EMPFANGEN
    // ============================================================
    public static void setFileInfo(PacketHeader header, String filename) {

        String key = header.sourceIp + ":" + header.sourcePort;
        FileBuffer fb = files.computeIfAbsent(key, __ -> new FileBuffer());

        fb.filename = filename;
    }

    // ============================================================
    // EINEN FILE_CHUNK VERARBEITEN
    // ============================================================
    public static void receiveChunk(PacketHeader header, byte[] payload) {

        String k = fileKey(header);
        FileBuffer fb = files.computeIfAbsent(k, __ -> new FileBuffer());

        // Falls FILE_INFO noch nicht erhalten wurde
        if (fb.totalChunks == -1) {
            fb.totalChunks = header.chunkLength;
        }

        fb.chunks.put(header.chunkId, payload);

        // ========================================================
        // FERTIG?
        // ========================================================
        if (fb.chunks.size() == fb.totalChunks) {

            byte[] data = mergeChunks(fb);

            try {
                Files.write(Paths.get(fb.filename), data);
            } catch (IOException ignored) {}

            // ACK zurück an Sender senden:
            //   → destIp = header.sourceIp
            //   → destPort = header.sourcePort
            var ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    header.sourceIp,
                    header.sourcePort & 0xFFFF
            );

            NodeContext.socket.sendPacket(
                    ack,
                    NodeContext.socket.socketAddressForIp(header.sourceIp),
                    header.sourcePort & 0xFFFF
            );

            files.remove(k);
            return;
        }

        // ========================================================
        // MISSING LIST ERSTELLEN
        // ========================================================
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < fb.totalChunks; i++) {
            if (!fb.chunks.containsKey(i)) missing.add(i);
        }

        if (missing.isEmpty()) return;

        int[] missingArr = missing.stream().mapToInt(x -> x).toArray();

        // NO_ACK zurück an Absender → MUSS an (sourceIp, sourcePort)
        var noAck = PacketFactory.createNoAck(
                header.sequenceNumber,
                header.sourceIp,
                header.sourcePort & 0xFFFF,
                missingArr
        );

        NodeContext.socket.sendPacket(
                noAck,
                NodeContext.socket.socketAddressForIp(header.sourceIp),
                header.sourcePort & 0xFFFF
        );
    }

    // ============================================================
    // CHUNKS ZUSAMMENBAUEN
    // ============================================================
    private static byte[] mergeChunks(FileBuffer fb) {

        int totalLen = fb.chunks.values().stream()
                .mapToInt(c -> c.length)
                .sum();

        byte[] out = new byte[totalLen];
        int pos = 0;

        for (int i = 0; i < fb.totalChunks; i++) {
            byte[] c = fb.chunks.get(i);
            System.arraycopy(c, 0, out, pos, c.length);
            pos += c.length;
        }

        return out;
    }
}