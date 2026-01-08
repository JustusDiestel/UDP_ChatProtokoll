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

    public static void sendFile(byte[] data,
                                int destIp,
                                int destPort,
                                String path) throws InterruptedException {

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Keine Route zum Ziel");
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);
        String filename = Paths.get(path).getFileName().toString();

        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        // =========================================================
        // EINE SequenceNumber für die GESAMTE DATEI
        // =========================================================
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

        // =========================================================
        // FRAMES (STOP-AND-WAIT)
        // =========================================================
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

            // =====================================================
            // ⛔ STOP-AND-WAIT:
            // Warten, bis KEIN Frame dieser Datei mehr pending ist
            // =====================================================
            while (PendingPackets.hasFrameForSequence(fileSeq)) {
                Thread.sleep(10);
            }

            // =====================================================
            // Frame JETZT erst registrieren
            // =====================================================
            PendingPackets.trackFrame(
                    framePackets,
                    fileSeq,
                    frameIndex,
                    destIp,
                    destPort
            );

            // =====================================================
            // Frame senden
            // =====================================================
            for (Packet p : framePackets) {
                NodeContext.socket.sendPacket(
                        p,
                        NodeContext.socket.socketAddressForIp(route.nextHopIp),
                        route.nextHopPort
                );
            }

            // =====================================================
            // Warten auf ACK(seq) oder NO_ACK → Resend passiert woanders
            // =====================================================
            int retries = 0;
            long lastSend = System.currentTimeMillis();

            while (PendingPackets.hasFrameForSequence(fileSeq)) {

                if (System.currentTimeMillis() - lastSend > 3000) {

                    if (++retries > 3) {
                        System.out.println(
                                "[FILE SEND ABORT] seq=" + fileSeq +
                                        " frameIndex=" + frameIndex
                        );
                        PendingPackets.dropFrame(fileSeq, frameIndex);
                        return; // Best effort
                    }

                    System.out.println(
                            "[FRAME RETRY] seq=" + fileSeq +
                                    " frameIndex=" + frameIndex +
                                    " attempt=" + retries
                    );

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

        System.out.println("[FILE SEND DONE] seq=" + fileSeq);
    }
}