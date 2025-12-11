package net.p2pchat.file;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

/**
 * Verantwortlich für selektives Wiederholen fehlender Chunks (NO_ACK).
 * Keine neuen SequenceNumbers!
 * Pakete werden NICHT neu erzeugt – die Originalpakete aus PendingPackets
 * werden erneut gesendet.
 */
public class FileResender {

    /**
     * Wird vom PacketReceiver beim Empfang eines NO_ACK aufgerufen.
     *
     * @param srcIp     IP des Empfängers (der NO_ACK geschickt hat)
     * @param srcPort   Port des Empfängers
     * @param frameSeq  SequenceNumber des Frames
     * @param missing   fehlende globale ChunkIDs
     */
    public static void resendChunks(int srcIp, int srcPort, int frameSeq, int[] missing) {

        PendingPackets.Pending p = PendingPackets.getPending().get(frameSeq);
        if (p == null || !p.isFrame) {
            return;
        }

        Packet[] framePackets = p.frameChunks;
        if (framePackets == null || framePackets.length == 0) {
            return;
        }

        var route = RoutingManager.getRoute(p.destIp, p.destPort);
        if (route == null) {
            return;
        }

        String nextHop = IpUtil.intToIp(route.nextHopIp);

        // =====================================================================
        // NEW CORRECT LOGIC:
        // missing[] enthält globale chunkId-Werte.
        // Wir müssen die entsprechenden Packets anhand ihres Header.chunkId finden.
        // =====================================================================
        for (int missingChunkId : missing) {

            Packet resend = null;

            // richtigen Chunk in frameChunks finden
            for (Packet fp : framePackets) {
                if (fp != null && fp.header.chunkId == missingChunkId) {
                    resend = fp;
                    break;
                }
            }

            if (resend == null) {
                System.out.println("WARN: Konnte fehlenden Chunk " + missingChunkId + " nicht finden.");
                continue;
            }

            NodeContext.socket.sendPacket(
                    resend,
                    NodeContext.socket.socketAddressForIp(route.nextHopIp),
                    route.nextHopPort
            );

            System.out.println(
                    "NO_ACK → fehlender Chunk " + missingChunkId +
                            " erneut gesendet (seq=" + resend.header.sequenceNumber + ")"
            );
        }

        // Timer wird NICHT zurückgesetzt → PendingPackets.updateMissingChunks() macht das
    }
}