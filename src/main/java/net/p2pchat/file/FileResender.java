package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileResender {

    private static final Map<Integer, Map<Integer, byte[]>> sentFiles = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> chunkCounts = new ConcurrentHashMap<>();

    public static void registerFile(int destIp, int totalChunks) {
        chunkCounts.put(destIp, totalChunks);
        sentFiles.putIfAbsent(destIp, new ConcurrentHashMap<>());
    }

    public static void registerChunk(int destIp, int chunkId, byte[] data) {
        sentFiles.putIfAbsent(destIp, new ConcurrentHashMap<>());
        sentFiles.get(destIp).put(chunkId, data);
    }

    public static void resendChunk(int destIp, int destPort, int chunkId) {
        Map<Integer, byte[]> chunks = sentFiles.get(destIp);
        if (chunks == null) {
            System.out.println("Resend: keine Chunks für destIp bekannt.");
            return;
        }

        byte[] chunk = chunks.get(chunkId);
        if (chunk == null) {
            System.out.println("Resend: Chunk " + chunkId + " nicht bekannt.");
            return;
        }

        int totalChunks = chunkCounts.getOrDefault(destIp, -1);
        if (totalChunks <= 0) {
            System.out.println("Resend: unknown totalChunks.");
            return;
        }

        int seq = NodeContext.seqGen.next();

        Packet p = PacketFactory.createFileChunk(
                seq,
                NodeContext.localIp,
                destIp,
                chunkId,
                totalChunks,
                chunk
        );

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Resend: keine Route.");
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);
        NodeContext.socket.sendReliable(p, nextHop, route.nextHopPort);

        System.out.println("Chunk " + chunkId + " erneut gesendet → " + nextHop + ":" + route.nextHopPort);
    }
}