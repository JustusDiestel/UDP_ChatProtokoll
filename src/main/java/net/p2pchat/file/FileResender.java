package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

/**
 * Verantwortlich für das selektive Wiederholen fehlender Chunks (NO_ACK).
 * Keine neuen SequenceNumbers!
 * Keine Regeneration von Paketen!
 * Es werden die Original-Pakete aus PendingPackets.frameChunks genutzt.
 */
public class FileResender {

    /**
     * Wird von PacketReceiver aufgerufen, wenn ein NO_ACK empfangen wurde.
     *
     * @param srcIp     IP des Empfängers
     * @param srcPort   Port des Empfängers
     * @param frameSeq  sequenceNumber des ersten Chunks des Frames
     * @param missing   fehlende globale Chunk-IDs
     */
    public static void resendChunks(int srcIp, int srcPort, int frameSeq, int[] missing) {

        PendingPackets.Pending p = PendingPackets.getPending().get(frameSeq);
        if (p == null || !p.isFrame)
            return;

        Packet[] frame = p.frameChunks;
        if (frame == null || frame.length == 0)
            return;

        var route = RoutingManager.getRoute(p.destIp, p.destPort);
        if (route == null)
            return;

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        for (int missingId : missing) {

            int frameIndex = missingId % frame.length;
            Packet resend = frame[frameIndex];

            if (resend == null)
                continue;

            NodeContext.socket.sendPacket(
                    resend,
                    NodeContext.socket.socketAddressForIp(route.nextHopIp),
                    route.nextHopPort
            );

            System.out.println(
                    "NO_ACK → Chunk " + missingId +
                            " erneut gesendet (seq=" + resend.header.sequenceNumber + ")"
            );
        }
    }
}