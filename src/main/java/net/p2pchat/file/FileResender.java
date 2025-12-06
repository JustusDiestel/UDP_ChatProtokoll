package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileResender {

    private static final Map<String, Map<Integer, byte[]>> sentFiles = new ConcurrentHashMap<>();
    private static final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static void registerFile(int destIp, int destPort, int totalChunks) {
        String k = key(destIp, destPort);
        chunkCounts.put(k, totalChunks);
        sentFiles.putIfAbsent(k, new ConcurrentHashMap<>());
    }

    public static void registerChunk(int destIp, int destPort, int chunkId, byte[] data) {
        String k = key(destIp, destPort);
        sentFiles.putIfAbsent(k, new ConcurrentHashMap<>());
        sentFiles.get(k).put(chunkId, data);
    }

    public static void resendChunks(int destIp, int destPort, int[] missing) {
        String k = key(destIp, destPort);

        Map<Integer, byte[]> chunks = sentFiles.get(k);
        if (chunks == null) return;

        Integer totalChunks = chunkCounts.get(k);
        if (totalChunks == null || totalChunks <= 0) return;

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) return;

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        for (int chunkId : missing) {
            byte[] data = chunks.get(chunkId);
            if (data == null) continue;

            int seq = NodeContext.seqGen.next();

            Packet p = PacketFactory.createFileChunk(
                    seq,
                    destIp,
                    destPort,
                    chunkId,
                    totalChunks,
                    data
            );

            NodeContext.socket.sendReliable(p, nextHop, route.nextHopPort);
        }
    }
}