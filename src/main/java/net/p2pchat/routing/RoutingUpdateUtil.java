package net.p2pchat.routing;

import net.p2pchat.NodeContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RoutingUpdateUtil {

    public static byte[] buildPayloadFromRoutingTable() {

        Collection<Route> all = RoutingTable.getAll().values();
        List<Route> filtered = new ArrayList<>();

        for (Route r : all) {
            if (r.destIp == NodeContext.localIp && r.destPort == NodeContext.localPort) continue;
            filtered.add(r);
        }

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