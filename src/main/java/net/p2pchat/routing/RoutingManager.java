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

                var p = PacketFactory.createRoutingUpdate(
                        seq,
                        n.ip,
                        n.port,
                        payload
                );

                String destIpStr = IpUtil.intToIp(n.ip);

                NodeContext.socket.sendPacket(
                        p,
                        InetAddress.getByName(destIpStr),
                        n.port
                );
            }

        } catch (Exception e) {
            System.err.println("Fehler beim Senden von ROUTING_UPDATE: " + e.getMessage());
        }
    }

    public static void sendMsg(int destIp, int destPort, String text) {
        Route r = RoutingTable.getRoute(destIp, destPort);
        if (r == null) return;

        try {
            byte[] payload = text.getBytes();
            int seq = NodeContext.seqGen.next();

            PacketHeader h = new PacketHeader();
            h.type = 0x05;
            h.sequenceNumber = seq;
            h.sourceIp = NodeContext.localIp;
            h.sourcePort = (short) NodeContext.localPort;
            h.destinationIp = destIp;
            h.destinationPort = (short) destPort;
            h.payloadLength = payload.length;
            h.ttl = 10;
            h.computeChecksum(payload);

            Packet msg = new Packet(h, payload);

            String nextHop = IpUtil.intToIp(r.nextHopIp);

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