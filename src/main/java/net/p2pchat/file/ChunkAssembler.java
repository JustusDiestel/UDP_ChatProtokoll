package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.model.PacketHeader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkAssembler {

    // key: (sourceIp + ":" + sequenceRange) / but simply sourceIp is ok for now
    private static final Map<Integer, FileBuffer> files = new ConcurrentHashMap<>();

    private static class FileBuffer {
        public int totalChunks;
        public Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    public static void receiveChunk(PacketHeader header, byte[] payload) {

        int src = header.sourceIp;

        files.computeIfAbsent(src, __ -> new FileBuffer());
        FileBuffer fb = files.get(src);

        // Set size if not known yet
        fb.totalChunks = header.chunkLength;
        fb.chunks.put(header.chunkId, payload);

        System.out.println("Chunk " + header.chunkId + "/" + header.chunkLength + " erhalten.");

        // Prüfen ob vollständig
        if (fb.chunks.size() == fb.totalChunks) {
            System.out.println("Datei vollständig erhalten!");

            // Zusammensetzen
            byte[] fileData = mergeChunks(fb);

            // Der Empfänger sendet ACK für komplette Datei
            PacketFactory.createAck(
                    header.sequenceNumber,
                    NodeContext.localIp,
                    header.sourceIp
            );

            // TODO: Datei speichern oder weiterverarbeiten

            // Buffer löschen
            files.remove(src);
        } else {
            // Falls Chunk fehlt → NO_ACK
            for (int i = 0; i < fb.totalChunks; i++) {
                if (!fb.chunks.containsKey(i)) {
                    System.out.println("Chunk fehlt: " + i);

                    var noAck = PacketFactory.createNoAck(
                            NodeContext.seqGen.next(),
                            NodeContext.localIp,
                            header.sourceIp,
                            i
                    );

                    // original port musst du weiterreichen, siehe PacketReceiver
                    // NodeContext.socket.sendPacket(noAck, ...)

                    break;
                }
            }
        }
    }

    private static byte[] mergeChunks(FileBuffer fb) {
        List<byte[]> l = new ArrayList<>();
        int totalLength = 0;

        for (int i = 0; i < fb.totalChunks; i++) {
            byte[] c = fb.chunks.get(i);
            l.add(c);
            totalLength += c.length;
        }

        byte[] out = new byte[totalLength];
        int pos = 0;

        for (byte[] chunk : l) {
            System.arraycopy(chunk, 0, out, pos, chunk.length);
            pos += chunk.length;
        }

        return out;
    }
}