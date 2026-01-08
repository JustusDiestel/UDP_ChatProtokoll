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

            // eigene Adresse niemals senden
            if (r.destIp == NodeContext.localIp &&
                    r.destPort == NodeContext.localPort)
                continue;

            // Route zum Nachbarn selbst niemals senden
            if (r.destIp == neighborIp &&
                    r.destPort == neighborPort)
                continue;

            // =========================
            // TODES-POISON (NUR DANN!)
            // =========================
            if (r.distance >= 255) {

                // niemals Poison zurück an den,
                // von dem wir die Route gelernt haben
                if (r.nextHopIp == neighborIp &&
                        r.nextHopPort == neighborPort)
                    continue;

                // einmal als 255 announcen
                filtered.add(new Route(
                        r.destIp,
                        r.destPort,
                        r.nextHopIp,
                        r.nextHopPort,
                        255
                ));
                continue;
            }

            // =========================
            // SPLIT HORIZON (LEBEND)
            // =========================
            if (r.nextHopIp == neighborIp &&
                    r.nextHopPort == neighborPort)
                continue;

            // normale lebende Route
            filtered.add(new Route(
                    r.destIp,
                    r.destPort,
                    r.nextHopIp,
                    r.nextHopPort,
                    Math.min(r.distance, 254)
            ));
        }

        // Payload erstellen
        int entryCount = filtered.size();
        int payloadSize = 2 + entryCount * 7;

        ByteBuffer buf = ByteBuffer.allocate(payloadSize);
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);
        buf.putShort((short) entryCount);

        for (Route r : filtered) {
            buf.putInt(r.destIp);
            buf.putShort((short) r.destPort);
            buf.put((byte) r.distance);
        }

        return buf.array();
    }
}