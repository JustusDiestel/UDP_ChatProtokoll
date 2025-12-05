package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.model.Packet;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSender {

    private static final Map<Integer, Integer> chunkCounts = new ConcurrentHashMap<>();

    public static int getChunkCount(int destIp) {
        return chunkCounts.getOrDefault(destIp, -1);
    }

    public static void sendFile(byte[] data, int destIp, int destPort) {

        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        chunkCounts.put(destIp, totalChunks);

        System.out.println("Sende Datei in " + totalChunks + " Chunks...");

        for (int i = 0; i < totalChunks; i++) {

            byte[] chunk = chunks.get(i);

            // für NO_ACK speichern
            FileResender.registerChunk(destIp, i, chunk);

            int seq = NodeContext.seqGen.next();

            Packet p = PacketFactory.createFileChunk(
                    seq,
                    NodeContext.localIp,
                    destIp,
                    i,
                    totalChunks,
                    chunk
            );

            var route = RoutingManager.getRoute(destIp, destPort);
            if (route == null) {
                System.out.println("Keine Route für Dateiübertragung.");
                return;
            }

            String nextHop = IpUtil.intToIp(route.nextHopIp);

            NodeContext.socket.sendReliable(
                    p,
                    nextHop,
                    route.nextHopPort
            );
        }

        System.out.println("Alle Chunks gesendet.");
    }
}