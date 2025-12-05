package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.protocol.PacketFactory;
import org.w3c.dom.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkAssembler {

    private static class FileBuffer {
        int totalChunks = -1;
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    // key: sourceIp
    private static final Map<Integer, FileBuffer> files = new ConcurrentHashMap<>();

    public static void receiveChunk(PacketHeader header, byte[] payload, int senderPort) {
        int src = header.sourceIp;

        FileBuffer fb = files.computeIfAbsent(src, __ -> new FileBuffer());

        if (fb.totalChunks == -1) {
            fb.totalChunks = header.chunkLength;
        }

        fb.chunks.put(header.chunkId, payload);

        System.out.println("FILE_CHUNK von " + src +
                " Chunk " + header.chunkId + "/" + (fb.totalChunks - 1));

        // Prüfen, ob komplett
        if (fb.chunks.size() == fb.totalChunks) {
            System.out.println("Datei von " + src + " vollständig empfangen.");

            byte[] fileData = mergeChunks(fb);

            // TODO: fileData irgendwo speichern

            // ACK für Datei senden – hier nehmen wir einfach die seq des letzten Chunks
            int ackSeq = header.sequenceNumber;
            var ack = PacketFactory.createAck(
                    ackSeq,
                    NodeContext.localIp,
                    header.sourceIp
            );
            // SenderPort kam aus DatagramPacket
            NodeContext.socket.sendPacket(ack,
                    NodeContext.socket.socketAddressForIp(header.sourceIp), // s.u. oder packet.getAddress()
                    senderPort
            );

            files.remove(src);
        } else {
            // Noch nicht fertig → fehlende Chunks suchen und evtl. NO_ACK schicken
            for (int i = 0; i < fb.totalChunks; i++) {
                if (!fb.chunks.containsKey(i)) {
                    System.out.println("Chunk fehlt: " + i);

                    var noAck = PacketFactory.createNoAck(
                            NodeContext.seqGen.next(),
                            NodeContext.localIp,
                            header.sourceIp,
                            i
                    );
                    // NO_ACK direkt an den Absender zurück
                    // (Adresse hast du im PacketReceiver als packet.getAddress()/getPort())
                    // → dort übergibst du senderPort / InetAddress.
                    NodeContext.socket.sendPacket(noAck,
                            NodeContext.socket.socketAddressForIp(header.sourceIp),
                            senderPort);

                    break;
                }
            }
        }
    }

    private static byte[] mergeChunks(FileBuffer fb) {
        List<byte[]> ordered = new ArrayList<>();
        int totalLength = 0;

        for (int i = 0; i < fb.totalChunks; i++) {
            byte[] c = fb.chunks.get(i);
            if (c == null) {
                // sollte bei vollständiger Datei nicht vorkommen
                continue;
            }
            ordered.add(c);
            totalLength += c.length;
        }

        byte[] out = new byte[totalLength];
        int pos = 0;
        for (byte[] chunk : ordered) {
            System.arraycopy(chunk, 0, out, pos, chunk.length);
            pos += chunk.length;
        }
        return out;
    }
}