package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.model.Packet;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileSender {

    private static final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static void sendFile(byte[] data, int destIp, int destPort, String path) {

        var route = RoutingManager.getRoute(destIp, destPort);
        if (route == null) {
            System.out.println("Keine Route für Dateiübertragung.");
            return;
        }

        // ECHTEN Dateinamen extrahieren
        String filename = Paths.get(path).getFileName().toString();

        List<byte[]> chunks = Chunker.split(data);
        int totalChunks = chunks.size();

        FileResender.registerFile(destIp, destPort, totalChunks);

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        // FILE_INFO senden (NICHT reliable)
        int seqInfo = NodeContext.seqGen.next();
        Packet info = PacketFactory.createFileInfo(
                seqInfo,
                destIp,
                destPort,
                totalChunks,
                filename              // <- HIER der echte Dateiname
        );

        NodeContext.socket.sendPacket(
                info,
                NodeContext.socket.socketAddressForIp(route.nextHopIp),
                route.nextHopPort
        );

        System.out.println("FILE_INFO gesendet: " + filename + " | Chunks=" + totalChunks);

        // Datei-Chunks senden (RELIABLE)
        for (int i = 0; i < totalChunks; i++) {

            byte[] chunk = chunks.get(i);
            FileResender.registerChunk(destIp, destPort, i, chunk);

            int seq = NodeContext.seqGen.next();

            Packet p = PacketFactory.createFileChunk(
                    seq,
                    destIp,
                    destPort,
                    i,
                    totalChunks,
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