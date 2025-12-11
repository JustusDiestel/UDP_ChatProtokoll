package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;
import java.util.Map;

public class RoutingManager {

    /**
     * Broadcastet Routing-Updates an alle lebenden Nachbarn.
     * Für jeden Nachbarn wird ein EIGENES Payload generiert
     * (Split Horizon + Poison Reverse).
     */
    public static void broadcastRoutingUpdate() {

        for (Map.Entry<String, Neighbor> entry : NeighborManager.getAll().entrySet()) {

            Neighbor n = entry.getValue();
            if (!n.alive) continue;

            try {
                // Payload mit Split Horizon / Poison Reverse
                byte[] payload = RoutingUpdateUtil.buildPayloadForNeighbor(n.ip, n.port);

                int seq = NodeContext.seqGen.next();

                Packet update = PacketFactory.createRoutingUpdate(
                        seq,
                        n.ip,      // destinationIp
                        n.port,    // destinationPort
                        payload
                );

                InetAddress addr = InetAddress.getByName(IpUtil.intToIp(n.ip));

                NodeContext.socket.sendPacket(update, addr, n.port);

                System.out.println("ROUTING_UPDATE → "
                        + IpUtil.intToIp(n.ip) + ":" + n.port
                        + " | entries=" + (payload.length / 7));

            } catch (Exception e) {
                System.err.println(
                        "Fehler bei ROUTING_UPDATE an "
                                + IpUtil.intToIp(n.ip) + ":" + n.port
                                + " → " + e.getMessage()
                );
            }
        }
    }


    /**
     * MSG zuverlässig über die Routing-Tabelle senden.
     */
    public static void sendMsg(int destIp, int destPort, String text) {

        Route r = RoutingTable.getRoute(destIp, destPort);
        if (r == null) {
            System.out.println("Keine Route zu " + destIp + ":" + destPort);
            return;
        }

        try {
            int seq = NodeContext.seqGen.next();

            Packet msg = PacketFactory.createMessage(
                    seq,
                    destIp,
                    destPort,
                    text
            );

            String nextHopStr = IpUtil.intToIp(r.nextHopIp);

            NodeContext.socket.sendReliable(
                    msg,
                    nextHopStr,
                    r.nextHopPort
            );

            System.out.println("MSG gesendet → \"" + text + "\" via "
                    + nextHopStr + ":" + r.nextHopPort);

        } catch (Exception e) {
            System.err.println("Fehler beim MSG-Senden: " + e.getMessage());
        }
    }


    // Convenience-Methoden
    public static Route getRoute(int destIp, int destPort) {
        return RoutingTable.getRoute(destIp, destPort);
    }

    public static int getNextHopIp(int destIp, int destPort) {
        Route r = RoutingTable.getRoute(destIp, destPort);
        return (r != null) ? r.nextHopIp : -1;
    }

    public static int getNextHopPort(int destIp, int destPort) {
        Route r = RoutingTable.getRoute(destIp, destPort);
        return (r != null) ? r.nextHopPort : -1;
    }
}