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

    private static final int FRAME_SIZE = 128;

    public static void sendFile(byte[] data, int destIp, int destPort, String path) {

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) return;

        String nextHop = IpUtil.intToIp(route.nextHopIp);
        String filename = Paths.get(path).getFileName().toString();

        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        // ===== EINE SequenceNumber für die GESAMTE DATEI =====
        int fileSeq = NodeContext.seqGen.next();

        // ---------------- FILE_INFO ----------------
        Packet info = PacketFactory.createFileInfo(
                fileSeq,
                destIp,
                destPort,
                totalChunks,
                filename
        );

        NodeContext.socket.sendReliable(info, nextHop, route.nextHopPort);

        // ---------------- FRAMES ----------------
        int chunkIndex = 0;

        while (chunkIndex < totalChunks) {

            int count = Math.min(FRAME_SIZE, totalChunks - chunkIndex);
            Packet[] framePackets = new Packet[count];

            int frameIndex = chunkIndex / FRAME_SIZE;

            for (int i = 0; i < count; i++) {
                int chunkId = chunkIndex + i;
                framePackets[i] = PacketFactory.createFileChunk(
                        fileSeq,                 // ✅ GLEICHE SequenceNumber
                        destIp,
                        destPort,
                        chunkId,
                        totalChunks,
                        chunks.get(chunkId)
                );
            }
            PendingPackets.trackFrame(
                    framePackets,
                    fileSeq,
                    frameIndex,
                    destIp,
                    destPort
            );

            for (Packet p : framePackets) {
                NodeContext.socket.sendPacket(
                        p,
                        NodeContext.socket.socketAddressForIp(route.nextHopIp),
                        route.nextHopPort
                );
            }

            chunkIndex += count;
        }
    }
}