package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.Route;
import net.p2pchat.routing.RoutingTable;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;

public class FileResender {

    public static void resendChunks(int sequenceNumber,
                                    int frameIndex,
                                    int[] missing) {

        PendingPackets.Pending p =
                PendingPackets.getPending()
                        .get((((long) sequenceNumber) << 32) | (frameIndex & 0xffffffffL));

        if (p == null) return;

        Route r = RoutingTable.getRoute(p.destIp, p.destPort);
        if (r == null) return;

        InetAddress nextHop;
        try {
            nextHop = InetAddress.getByName(IpUtil.intToIp(r.nextHopIp));
        } catch (Exception e) {
            return;
        }

        for (int chunkId : missing) {
            Packet pkt = p.frameChunks[chunkId % 128];
            if (pkt != null) {
                NodeContext.socket.sendPacket(pkt, nextHop, r.nextHopPort);
            }
        }
    }
}