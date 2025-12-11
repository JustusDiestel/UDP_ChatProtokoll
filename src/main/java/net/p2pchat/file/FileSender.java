package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.nio.file.Paths;
import java.util.List;

public class FileSender {

    public static void sendFile(byte[] data, int destIp, int destPort, String path) {

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Keine Route für Dateiübertragung.");
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        // =====================================================================
        // 1) Echtem Dateinamen extrahieren
        // =====================================================================
        String filename = Paths.get(path).getFileName().toString();

        // =====================================================================
        // 2) Datei in 1000-Byte-Chunks splitten
        // =====================================================================
        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        // =====================================================================
        // 3) FILE_INFO — reliable
        // =====================================================================
        int seqInfo = NodeContext.seqGen.next();

        Packet info = PacketFactory.createFileInfo(
                seqInfo,
                destIp,
                destPort,
                totalChunks,
                filename
        );

        NodeContext.socket.sendReliable(
                info,
                nextHop,
                route.nextHopPort
        );

        System.out.println("FILE_INFO gesendet → " + filename +
                " | totalChunks=" + totalChunks);


        // =====================================================================
        // 4) Datei in Frames à 128 Chunks senden
        // =====================================================================
        final int FRAME_SIZE = 128;
        int currentChunk = 0;

        while (currentChunk < totalChunks) {

            int remaining = totalChunks - currentChunk;
            int frameChunkCount = Math.min(FRAME_SIZE, remaining);

            // FrameStartSeq = sequenceNumber des ersten Chunks
            int frameStartSeq = NodeContext.seqGen.next();

            Packet[] framePackets = new Packet[frameChunkCount];

            for (int i = 0; i < frameChunkCount; i++) {

                int globalChunkId = currentChunk + i;
                byte[] chunkData = chunks.get(globalChunkId);

                int seq = frameStartSeq + i;

                Packet p = PacketFactory.createFileChunk(
                        seq,
                        destIp,
                        destPort,
                        globalChunkId,
                        totalChunks,
                        chunkData
                );

                framePackets[i] = p;
            }

            // Frame für Reliability registrieren
            PendingPackets.trackFrame(
                    framePackets,
                    frameStartSeq,
                    destIp,
                    destPort
            );

            // Frame senden (unreliable)
            for (Packet fp : framePackets) {
                NodeContext.socket.sendPacket(
                        fp,
                        NodeContext.socket.socketAddressForIp(route.nextHopIp),
                        route.nextHopPort
                );
            }

            System.out.println(
                    "Frame gesendet: startSeq=" + frameStartSeq +
                            " | Chunks=" + frameChunkCount +
                            " | globalStart=" + currentChunk
            );

            currentChunk += frameChunkCount;
        }

        System.out.println("Alle Frames gesendet – Reliability läuft nun.");
    }
}