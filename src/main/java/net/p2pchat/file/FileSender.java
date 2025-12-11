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
            System.out.println("Keine Route.");
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        String filename = Paths.get(path).getFileName().toString();
        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        // ============================================================
        // 1) EINE seqNr für FILE_INFO + ALLE Chunks
        // ============================================================
        int fileSeq = NodeContext.seqGen.next();

        // FILE_INFO senden (reliable)
        Packet info = PacketFactory.createFileInfo(
                fileSeq,
                destIp,
                destPort,
                filename
        );
        NodeContext.socket.sendReliable(info, nextHop, route.nextHopPort);

        System.out.println("FILE_INFO seq=" + fileSeq + " → " + filename);

        final int FRAME_SIZE = 128;
        int currentChunk = 0;

        while (currentChunk < totalChunks) {

            int remaining = totalChunks - currentChunk;
            int count = Math.min(FRAME_SIZE, remaining);

            Packet[] framePackets = new Packet[count];

            for (int i = 0; i < count; i++) {

                int chunkId = currentChunk + i;
                byte[] chunkData = chunks.get(chunkId);

                Packet p = PacketFactory.createFileChunk(
                        fileSeq,           // ALLE Frames + FILE_INFO = gleiche seq
                        destIp,
                        destPort,
                        chunkId,
                        totalChunks,
                        chunkData
                );

                framePackets[i] = p;
            }

            PendingPackets.trackFrame(framePackets, fileSeq, destIp, destPort);

            for (Packet fp : framePackets) {
                NodeContext.socket.sendPacket(
                        fp,
                        NodeContext.socket.socketAddressForIp(route.nextHopIp),
                        route.nextHopPort
                );
            }

            System.out.println("Frame gesendet: seq=" + fileSeq +
                    " | chunks=" + count + " | start=" + currentChunk);

            currentChunk += count;
        }
    }
}