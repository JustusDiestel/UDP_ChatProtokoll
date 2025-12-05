package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileResender {

    // Speichert gesendete Chunks: key=(destIp), value = map(chunkId -> data)
    // Bei Bedarf kannst du später ein echtes File-ID Konzept einbauen.
    private static final Map<Integer, Map<Integer, byte[]>> sentFiles = new ConcurrentHashMap<>();

    public static void registerChunk(int destIp, int chunkId, byte[] data) {
        sentFiles.putIfAbsent(destIp, new ConcurrentHashMap<>());
        sentFiles.get(destIp).put(chunkId, data);
    }

    public static void resendChunk(int destIp, int destPort, int chunkId) {

        if (!sentFiles.containsKey(destIp)) {
            System.out.println("Resend-Error: Keine Datei für diesen Empfänger registriert.");
            return;
        }

        byte[] chunk = sentFiles.get(destIp).get(chunkId);

        if (chunk == null) {
            System.out.println("Resend-Error: Chunk nicht bekannt.");
            return;
        }

        // Neue SequenceNumber für Resend
        int seq = NodeContext.seqGen.next();

        // ChunkCount unbekannt? Nein: ChunkCount muss beim Senden bekannt sein.
        // Wir speichern chunkCount also im FileSender ab (siehe unten).
        int chunkCount = FileSender.getChunkCount(destIp);

        Packet p = PacketFactory.createFileChunk(
                seq,
                NodeContext.localIp,
                destIp,
                chunkId,
                chunkCount,
                chunk
        );

        // Routing benutzen
        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Resend-Error: Keine Route verfügbar.");
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        NodeContext.socket.sendReliable(
                p,
                nextHop,
                route.nextHopPort
        );

        System.out.println("Fehlender Chunk " + chunkId + " erneut gesendet → " + nextHop);
    }
}