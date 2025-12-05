package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.model.Packet;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;

public class RoutingManager {

    public static void broadcastRoutingUpdate() {
        try {
            byte[] payload = RoutingUpdateUtil.buildPayloadFromRoutingTable();

            for (var entry : NeighborManager.getAll().entrySet()) {
                Neighbor n = entry.getValue();
                if (!n.alive) continue;

                int seq = NodeContext.seqGen.next();

                Packet p = PacketFactory.createRoutingUpdate(
                        seq,
                        NodeContext.localIp,
                        n.ip,
                        payload
                );

                String destIpStr = IpUtil.intToIp(n.ip);

                NodeContext.socket.sendPacket(
                        p,
                        InetAddress.getByName(destIpStr),
                        n.port
                );

                System.out.println("ROUTING_UPDATE an Nachbarn " + destIpStr + ":" + n.port +
                        " (seq=" + seq + ")");
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Senden von ROUTING_UPDATE: " + e.getMessage());
        }
    }
    public static void sendMsg(int destIp, int destPort, String text) {
        Route r = RoutingTable.getRoute(destIp, destPort);

        if (r == null) {
            System.out.println("Keine Route zu " + destIp + ":" + destPort);
            return;
        }

        try {
            byte[] payload = text.getBytes();

            int seq = NodeContext.seqGen.next();

            PacketHeader header = new PacketHeader();
            header.type = 0x05; // MSG
            header.sequenceNumber = seq;
            header.sourceIp = NodeContext.localIp;
            header.destinationIp = destIp;
            header.payloadLength = payload.length;
            header.ttl = 10; // Standard
            header.computeChecksum(payload);

            Packet msg = new Packet(header, payload);

            String nextHop = IpUtil.intToIp(r.nextHopIp);

            System.out.println("Sende MSG → " + nextHop + ":" + r.nextHopPort +
                    " (Ziel = " + destIp + ":" + destPort + ")");

            NodeContext.socket.sendReliable(
                    msg,
                    nextHop,
                    r.nextHopPort
            );

        } catch (Exception e) {
            System.err.println("Fehler beim MSG-Senden: " + e.getMessage());
        }
    }

    public static Route getRoute(int destIp, int destPort) {
        return RoutingTable.getRoute(destIp, destPort);
    }

    public static int getNextHopIp(int destIp, int destPort) {
        var r = RoutingTable.getRoute(destIp, destPort);
        return (r != null) ? r.nextHopIp : -1;
    }

    public static int getNextHopPort(int destIp, int destPort) {
        var r = RoutingTable.getRoute(destIp, destPort);
        return (r != null) ? r.nextHopPort : -1;
    }
}