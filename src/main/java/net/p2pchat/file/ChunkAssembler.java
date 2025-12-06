package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.protocol.PacketFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkAssembler {

    private static class FileBuffer {
        int totalChunks = -1;
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    private static final Map<String, FileBuffer> files = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static void receiveChunk(PacketHeader header, byte[] payload, int senderPort) {

        String k = key(header.sourceIp, header.sourcePort & 0xFFFF);
        FileBuffer fb = files.computeIfAbsent(k, __ -> new FileBuffer());

        if (fb.totalChunks == -1) {
            fb.totalChunks = header.chunkLength;
        }

        fb.chunks.put(header.chunkId, payload);

        System.out.println("FILE_CHUNK von " + k +
                " Chunk " + header.chunkId + "/" + (fb.totalChunks - 1));

        if (fb.chunks.size() == fb.totalChunks) {

            System.out.println("Datei vollständig empfangen von " + k);

            byte[] data = mergeChunks(fb);

            int ackSeq = header.sequenceNumber;

            var ack = PacketFactory.createAck(
                    ackSeq,
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
        List<byte[]> ordered = new ArrayList<>();
        int totalLength = 0;

        for (int i = 0; i < fb.totalChunks; i++) {
            byte[] c = fb.chunks.get(i);
            ordered.add(c);
            totalLength += c.length;
        }

        byte[] out = new byte[totalLength];
        int pos = 0;

        for (byte[] part : ordered) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }

        return out;
    }
}