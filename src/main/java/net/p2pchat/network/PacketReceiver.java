package net.p2pchat.network;

import net.p2pchat.NodeContext;
import net.p2pchat.file.ChunkAssembler;
import net.p2pchat.file.FileResender;
import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.NeighborManager;
import net.p2pchat.routing.Route;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.routing.RoutingTable;
import net.p2pchat.util.HashUtil;
import net.p2pchat.util.IpUtil;
import net.p2pchat.util.ReceivedHistory;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PacketReceiver {

    private static final ReceivedHistory receivedHistory = new ReceivedHistory();

    public static void handle(DatagramPacket packet) {

        // ===================== UDP-Envelope (DIRECT NEIGHBOR) =====================
        int hopIp = IpUtil.ipToInt(packet.getAddress().getHostAddress());
        int hopPort = packet.getPort();

        // ===================== RAW =====================
        int len = packet.getLength();
        byte[] raw = Arrays.copyOfRange(
                packet.getData(),
                packet.getOffset(),
                packet.getOffset() + len
        );

        PacketHeader header = PacketHeader.fromBytes(raw);

        // ===================== ORIGINAL HEADER FIELDS =====================
        int senderIp = header.sourceIp;
        int senderPort = header.sourcePort & 0xFFFF;
        int typeName = header.type;
        int destIp = header.destinationIp;
        int destPort = header.destinationPort & 0xFFFF;

        int headerSize = PacketHeader.HEADER_SIZE;
        if (raw.length < headerSize + header.payloadLength) return;

        byte[] payload = Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);

        if (!Arrays.equals(HashUtil.sha256(payload), header.checksum)) return;

        // Neues: gemeinsame Prüfung, ob das Paket für diesen Knoten bestimmt ist,
        // und kurzes Logging wenn ja.
        boolean destinedForMe =
                destIp == NodeContext.localIp &&
                        destPort == NodeContext.localPort;
/**
        if (destinedForMe) {
            System.out.println("[RECV] " + typeName
                    + " von " + IpUtil.intToIp(senderIp) + ":" + senderPort
                    + " seq=" + header.sequenceNumber
                    + " len=" + payload.length);
        }
**/
        // ===================== NEIGHBOR MGMT (HOP!) =====================
        boolean wasAliveBefore = NeighborManager.isAlive(hopIp, hopPort);

        if (header.type == 0x03 || header.type == 0x08) { // HELLO oder HEARTBEAT
            NeighborManager.updateOrAdd(hopIp, hopPort);
        }

        // ===================== ACK =====================
        if (header.type == 0x01) {
            if (header.payloadLength != 0) return;
            int seq = header.sequenceNumber;

            // gesamtes Frame dieser Datei bestätigt
            PendingPackets.getPending().entrySet().removeIf(e ->
                    e.getValue().isFrame && e.getValue().sequenceNumber == seq
            );

            PendingPackets.clearSingle(seq);
            return;
        }

        // ===================== NO_ACK =====================
        if (header.type == 0x02) {

            if (payload.length < 6) return;

            ByteBuffer buf = ByteBuffer.wrap(payload);
            int seq = buf.getInt();
            int count = buf.getShort() & 0xFFFF;

            if (buf.remaining() < count * 4) return;

            int[] missing = new int[count];
            for (int i = 0; i < count; i++) missing[i] = buf.getInt();

            int frameIndex = (count > 0) ? (missing[0] / 128) : 0;

            PendingPackets.updateMissingChunks(seq, frameIndex, missing);
            FileResender.resendChunks(seq, frameIndex, missing);
            System.out.println("[NO_ACK] seq=" + seq + " frameIndex=" + frameIndex + " missingCount=" + count);
            return;
        }

        // ===================== HELLO =====================
        if (header.type == 0x03) {
            if (header.payloadLength != 0) return;
            RoutingTable.ensureDirectNeighbor(hopIp, hopPort);
            if (!wasAliveBefore) RoutingManager.broadcastRoutingUpdate();
            return;
        }

        // ===================== GOODBYE =====================
        if (header.type == 0x04) {
            if (header.payloadLength != 0) return;
            NeighborManager.markDead(hopIp, hopPort);
            boolean changed = RoutingTable.removeVia(hopIp, hopPort);
            if (changed) RoutingManager.broadcastRoutingUpdate();
            return;
        }

        // ===================== MSG =====================
        if (header.type == 0x05) {

            boolean isForMe =
                    destIp == NodeContext.localIp &&
                            destPort == NodeContext.localPort;

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendReliable(
                        new Packet(fwd, payload),
                        IpUtil.intToIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            if (receivedHistory.isDuplicate(senderIp, senderPort, header.sequenceNumber)) {
                Packet ack = PacketFactory.createAck(
                        header.sequenceNumber,
                        senderIp,
                        senderPort
                );
                NodeContext.socket.sendPacket(ack, packet.getAddress(), senderPort);
                return;
            }

            String msg = new String(payload, StandardCharsets.UTF_8);
            System.out.println("MSG von "
                    + IpUtil.intToIp(senderIp) + ":" + senderPort
                    + " → " + msg);

            Packet ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    senderIp,
                    senderPort
            );
            NodeContext.socket.sendPacket(ack, packet.getAddress(), senderPort);
            return;
        }

        // ===================== FILE_CHUNK =====================
        if (header.type == 0x06) {
            System.out.println(
                    "[RECV FILE_CHUNK] seq=" + header.sequenceNumber +
                            " chunkId=" + header.chunkId +
                            " from=" + IpUtil.intToIp(senderIp) + ":" + senderPort
            );

            boolean isForMe =
                    destIp == NodeContext.localIp &&
                            destPort == NodeContext.localPort;

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendReliable(
                        new Packet(fwd, payload),
                        IpUtil.intToIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            ChunkAssembler.receiveChunk(header, payload);
            return;
        }

        // ===================== FILE_INFO =====================
        if (header.type == 0x07) {

            boolean isForMe =
                    destIp == NodeContext.localIp &&
                            destPort == NodeContext.localPort;

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendReliable(
                        new Packet(fwd, payload),
                        IpUtil.intToIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            String filename = new String(payload, StandardCharsets.UTF_8);
            ChunkAssembler.setFileInfo(header, filename);

            Packet ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    senderIp,
                    senderPort
            );
            NodeContext.socket.sendPacket(ack, packet.getAddress(), senderPort);
            return;
        }

        // ===================== HEARTBEAT =====================
        if (header.type == 0x08) {
            if (header.payloadLength != 0) return;
            RoutingTable.ensureDirectNeighbor(hopIp, hopPort);
            if (!wasAliveBefore) RoutingManager.broadcastRoutingUpdate();
            return;
        }

        // ===================== ROUTING_UPDATE =====================
        if (header.type == 0x09) {

            if (payload.length < 2) return;

            // Routing Updates nur von bekannten Nachbarn akzeptieren
            if (!NeighborManager.isAlive(hopIp, hopPort)) return;

            boolean newNeighbor = false;
            ByteBuffer buf = ByteBuffer.wrap(payload);
            int count = buf.getShort() & 0xFFFF;

            boolean changed = false;

            for (int i = 0; i < count; i++) {
                if (buf.remaining() < 7) return;

                int dip = buf.getInt();
                int dport = buf.getShort() & 0xFFFF;
                int recvDist = buf.get() & 0xFF;

                if (dip == NodeContext.localIp && dport == NodeContext.localPort)
                    continue;

                if (RoutingTable.learnFromUpdate(
                        hopIp, hopPort,   // ← NEXT HOP!
                        dip, dport,
                        recvDist
                )) {
                    changed = true;
                }
            }
            if (changed) {
                RoutingManager.broadcastRoutingUpdate();
            }
        }
    }
}

