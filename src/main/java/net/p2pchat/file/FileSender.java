package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.model.Packet;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSender {

    private static final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static int getChunkCount(int destIp, int destPort) {
        return chunkCounts.getOrDefault(key(destIp, destPort), -1);
    }

    public static void sendFile(byte[] data, int destIp, int destPort) {

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Keine Route für Dateiübertragung.");
            return;
        }

        List<byte[]> chunks = Chunker.split(data);
        int total = chunks.size();

        chunkCounts.put(key(destIp, destPort), total);
        FileResender.registerFile(destIp, destPort, total);

        System.out.println("Sende Datei in " + total + " Chunks...");

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        for (int i = 0; i < total; i++) {

            byte[] chunk = chunks.get(i);
            FileResender.registerChunk(destIp, destPort, i, chunk);

            int seq = NodeContext.seqGen.next();

            Packet p = PacketFactory.createFileChunk(
                    seq,
                    destIp,
                    destPort,
                    i,
                    total,
                    chunk
            );

            NodeContext.socket.sendReliable(
                    p,
                    nextHop,
                    route.nextHopPort
            );
        }

        System.out.println("Alle Chunks gesendet.");
    }
}