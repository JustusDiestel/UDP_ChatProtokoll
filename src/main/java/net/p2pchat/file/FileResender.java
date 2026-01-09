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

        if (p == null) {
            System.err.println(
                    "[RESEND ERROR] Frame nicht gefunden: seq=" + sequenceNumber +
                            " frame=" + frameIndex
            );
            return;
        }

        Route r = RoutingTable.getRoute(p.destIp, p.destPort);
        if (r == null) {
            System.err.println(
                    "[RESEND ERROR] Keine Route: dest=" + p.destIp + ":" + p.destPort
            );
            return;
        }

        InetAddress nextHop;
        try {
            nextHop = InetAddress.getByName(IpUtil.intToIp(r.nextHopIp));
        } catch (Exception e) {
            System.err.println("[RESEND ERROR] Invalid IP: " + r.nextHopIp);
            return;
        }

        System.out.println(
                "[RESEND START] seq=" + sequenceNumber +
                        " frame=" + frameIndex +
                        " missing=" + missing.length + " chunks"
        );

        int resent = 0;
        for (int chunkId : missing) {

            // ⚠️ WICHTIG: localIndex basiert auf chunkId % FRAME_SIZE, nicht frameIndex!
            int localIndex = chunkId % 128;

            if (localIndex < 0 || localIndex >= p.frameChunks.length) {
                System.err.println(
                        "[RESEND ERROR] Invalid localIndex: chunkId=" + chunkId +
                                " localIndex=" + localIndex + " arrayLen=" + p.frameChunks.length
                );
                continue;
            }

            Packet pkt = p.frameChunks[localIndex];
            if (pkt != null) {
                NodeContext.socket.sendPacket(pkt, nextHop, r.nextHopPort);
                resent++;

                System.out.println(
                        "[RESEND] chunkId=" + chunkId +
                                " localIndex=" + localIndex
                );
            } else {
                System.err.println(
                        "[RESEND ERROR] Packet ist null: chunkId=" + chunkId +
                                " localIndex=" + localIndex
                );
            }
        }

        System.out.println(
                "[RESEND DONE] seq=" + sequenceNumber +
                        " frame=" + frameIndex +
                        " resent=" + resent + "/" + missing.length
        );
    }
}