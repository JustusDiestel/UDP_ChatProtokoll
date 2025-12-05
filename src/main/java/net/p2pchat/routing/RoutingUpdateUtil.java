package net.p2pchat.routing;

import java.nio.ByteBuffer;
import java.util.Collection;

public class RoutingUpdateUtil {

    /**
     * Baut den Payload für ein ROUTING_UPDATE (Type 0x08)
     * Format laut Spezifikation:
     *
     * 2 Bytes: EntryCount
     * Für jeden Eintrag:
     *   4 Bytes destIp
     *   2 Bytes destPort
     *   1 Byte distance
     */
    public static byte[] buildPayloadFromRoutingTable() {

        Collection<Route> routes = RoutingTable.getAll().values();

        int entryCount = routes.size();
        int payloadSize = 2 + entryCount * 7;

        ByteBuffer buf = ByteBuffer.allocate(payloadSize);

        // Anzahl Einträge (unsigned short)
        buf.putShort((short) entryCount);

        // 7-Byte Routing-Entries
        for (Route r : routes) {
            buf.putInt(r.destIp);                    // 4 Byte
            buf.putShort((short) r.destPort);        // 2 Byte
            buf.put((byte) r.distance);              // 1 Byte
        }

        return buf.array();
    }
}