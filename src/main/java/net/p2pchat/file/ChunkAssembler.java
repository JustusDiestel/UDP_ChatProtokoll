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

    private static String fileKey(PacketHeader h) {
        return h.sourceIp + ":" + h.destinationIp + ":" + (h.destinationPort & 0xFFFF);
    }

    public static void setFileInfo(PacketHeader header, int totalChunks, String originalName) {
        String k = fileKey(header);
        FileBuffer fb = files.computeIfAbsent(k, __ -> new FileBuffer());
        fb.totalChunks = totalChunks;

        int recvPort = header.destinationPort & 0xFFFF;

        int dot = originalName.lastIndexOf('.');
        String base = (dot > 0) ? originalName.substring(0, dot) : originalName;
        String ext = (dot > 0) ? originalName.substring(dot) : ".bin";

        fb.filename = base + "_" + recvPort + ext;
        System.out.println("FILE_INFO gespeichert → " + fb.filename + " (" + totalChunks + " Chunks)");
    }

    public static void receiveChunk(PacketHeader header, byte[] payload, int senderPort) {

        String k = fileKey(header);
        FileBuffer fb = files.computeIfAbsent(k, __ -> new FileBuffer());

        if (fb.totalChunks == -1) {
            fb.totalChunks = header.chunkLength;
        }

        fb.chunks.put(header.chunkId, payload);

        System.out.println("FILE_CHUNK " + header.chunkId + "/" + (fb.totalChunks - 1) + " von " + k);

        if (fb.chunks.size() == fb.totalChunks) {

            System.out.println("Datei vollständig empfangen von " + k);

            byte[] data = mergeChunks(fb);

            try {
                Files.write(Paths.get(fb.filename), data);
                System.out.println("Datei gespeichert als: " + fb.filename);
            } catch (IOException e) {
                System.out.println("Fehler beim Speichern der Datei: " + e.getMessage());
            }

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

        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < fb.totalChunks; i++) {
            if (!fb.chunks.containsKey(i)) missing.add(i);
        }

        if (missing.isEmpty()) return;

        int[] missingArr = missing.stream().mapToInt(x -> x).toArray();

        var noAck = PacketFactory.createNoAck(
                NodeContext.seqGen.next(),
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

    private static byte[] mergeChunks(FileBuffer fb) {
        int totalLen = fb.chunks.values().stream().mapToInt(c -> c.length).sum();

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