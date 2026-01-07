package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;
import net.p2pchat.protocol.PendingPackets;
import java.nio.file.Paths;
import java.util.List;

public class FileSender {

    private static final int FRAME_SIZE = 128;

    public static void sendFile(byte[] data, int destIp, int destPort, String path) throws InterruptedException {

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

        int frameIndex = 0;
        int chunkIndex = 0;

        while (chunkIndex < totalChunks) {

            int count = Math.min(FRAME_SIZE, totalChunks - chunkIndex);
            Packet[] framePackets = new Packet[count];

            for (int i = 0; i < count; i++) {
                int chunkId = chunkIndex + i;
                framePackets[i] = PacketFactory.createFileChunk(
                        fileSeq,
                        destIp,
                        destPort,
                        chunkId,
                        totalChunks,
                        chunks.get(chunkId)
                );
            }

            System.out.println(
                    "[FRAME START] seq=" + fileSeq +
                            " frameIndex=" + frameIndex +
                            " chunks=" + count
            );

            PendingPackets.trackFrame(framePackets, fileSeq, frameIndex, destIp, destPort);

            System.out.println(
                    "[PENDING CHECK] key=" +
                            ((((long) fileSeq) << 32) | (frameIndex & 0xffffffffL)) +
                            " pendingSize=" + PendingPackets.getPending().size()
            );

            for (Packet p : framePackets) {
                NodeContext.socket.sendPacket(
                        p,
                        NodeContext.socket.socketAddressForIp(route.nextHopIp),
                        route.nextHopPort
                );
            }

            int retries = 0;
            long lastSend = System.currentTimeMillis();

// ⛔ STOP-AND-WAIT: warte auf ACK(seq)
            while (PendingPackets.hasFrame(fileSeq, frameIndex)) {
                if (System.currentTimeMillis() - lastSend > 3000) {

                    if (++retries > 3) {
                        PendingPackets.dropFrame(fileSeq, frameIndex);
                        return; // best effort → abbrechen
                    }

                    // Frame erneut senden
                    for (Packet p : framePackets) {
                        NodeContext.socket.sendPacket(
                                p,
                                NodeContext.socket.socketAddressForIp(route.nextHopIp),
                                route.nextHopPort
                        );
                    }

                    lastSend = System.currentTimeMillis();
                }

                Thread.sleep(10);
            }

            chunkIndex += count;
            frameIndex++;
        }
    }
}