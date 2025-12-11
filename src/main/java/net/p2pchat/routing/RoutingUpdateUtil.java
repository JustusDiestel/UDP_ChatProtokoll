package net.p2pchat.routing;

import net.p2pchat.NodeContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoutingUpdateUtil {

    /**
     * Baut ein Routing-Update-Payload für GENAU EINEN Nachbarn.
     * Split Horizon + Poison Reverse vollständig implementiert.
     *
     * Format:
     * [2 bytes] entryCount
     * Für jeden Eintrag:
     *   [4 bytes] destIp
     *   [2 bytes] destPort
     *   [1 byte ] distance (0–255)
     */
    public static byte[] buildPayloadForNeighbor(int neighborIp, int neighborPort) {

        List<Route> tableCopy = new ArrayList<>();

        // Tabelle kopieren
        for (Map.Entry<String, Route> entry : RoutingTable.getAll().entrySet()) {
            tableCopy.add(entry.getValue().copy());
        }

        List<Route> filtered = new ArrayList<>();

        for (Route r : tableCopy) {

            // eigene Adresse nicht senden
            if (r.destIp == NodeContext.localIp &&
                    r.destPort == NodeContext.localPort)
                continue;

            // Split Horizon + Poison Reverse
            if (r.nextHopIp == neighborIp && r.nextHopPort == neighborPort) {

                Route poisoned = new Route(
                        r.destIp,
                        r.destPort,
                        r.nextHopIp,
                        r.nextHopPort,
                        255   // Poison Reverse
                );

                filtered.add(poisoned);
                continue;
            }

            // Normale Route
            int dist = Math.min(r.distance, 255);
            filtered.add(new Route(r.destIp, r.destPort, r.nextHopIp, r.nextHopPort, dist));
        }

        // Payload erstellen
        int entryCount = filtered.size();
        int payloadSize = 2 + entryCount * 7;

        ByteBuffer buf = ByteBuffer.allocate(payloadSize);
        buf.putShort((short) entryCount);

        for (Route r : filtered) {
            buf.putInt(r.destIp);
            buf.putShort((short) r.destPort);
            buf.put((byte) r.distance);
        }

        return buf.array();
    }
}