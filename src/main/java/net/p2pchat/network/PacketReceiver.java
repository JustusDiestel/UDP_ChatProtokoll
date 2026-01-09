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

    private static final int FRAME_SIZE = 128;
    private static final ReceivedHistory receivedHistory = new ReceivedHistory();

    public static void handle(DatagramPacket packet) {

        int hopIp = IpUtil.ipToInt(packet.getAddress().getHostAddress());
        int hopPort = packet.getPort();

        byte[] raw = Arrays.copyOfRange(
                packet.getData(),
                packet.getOffset(),
                packet.getOffset() + packet.getLength()
        );

        PacketHeader header = PacketHeader.fromBytes(raw);

        int senderIp = header.sourceIp;
        int senderPort = header.sourcePort & 0xFFFF;
        int destIp = header.destinationIp;
        int destPort = header.destinationPort & 0xFFFF;

        int headerSize = PacketHeader.HEADER_SIZE;
        if (raw.length < headerSize + header.payloadLength) return;

        byte[] payload = Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);
        if (!Arrays.equals(HashUtil.sha256(payload), header.checksum)) return;

        boolean isForMe =
                destIp == NodeContext.localIp &&
                        destPort == NodeContext.localPort;

        boolean wasAliveBefore = NeighborManager.isAlive(hopIp, hopPort);
        if (header.type == 0x03 || header.type == 0x08) {
            NeighborManager.updateOrAdd(hopIp, hopPort);
        }

        // ================= ACK =================
        if (header.type == 0x01) {

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;
                fwd.computeChecksum(payload);

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(fwd, payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            int seq = header.sequenceNumber;

            if (PendingPackets.getPending()
                    .containsKey((((long) seq) << 32) | 0xffffffffL)) {
                PendingPackets.clearSingle(seq);
                return;
            }

            for (PendingPackets.Pending p : PendingPackets.getPending().values()) {
                if (p.isFrame && p.sequenceNumber == seq) {
                    PendingPackets.clearFrame(seq, p.frameIndex);
                    break;
                }
            }
            return; // ✅ NICHT WEITERLEITEN
        }

        // ================= NO_ACK =================
        if (header.type == 0x02) {

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;
                fwd.computeChecksum(payload);

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(fwd, payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            if (payload.length < 6) return;

            ByteBuffer buf = ByteBuffer.wrap(payload);
            int seq = buf.getInt();
            int count = buf.getShort() & 0xFFFF;
            if (count == 0 || buf.remaining() < count * 4) return;

            int[] missing = new int[count];
            for (int i = 0; i < count; i++) missing[i] = buf.getInt();

            // ⚠️ KRITISCH: Alle Chunks müssen vom gleichen Frame sein!
            int frameIndex = missing[0] / FRAME_SIZE;
            for (int m : missing) {
                if (m / FRAME_SIZE != frameIndex) {
                    System.err.println(
                            "[NO_ACK ERROR] Chunks aus verschiedenen Frames: " +
                                    "first=" + missing[0] + " invalid=" + m
                    );
                    return;  // ❌ Ungültiges NO_ACK ignorieren
                }
            }

            System.out.println(
                    "[NO_ACK RECV] seq=" + seq +
                            " frameIndex=" + frameIndex +
                            " missing=" + missing.length + " chunks"
            );

            PendingPackets.updateMissingChunks(seq, frameIndex, missing);
            FileResender.resendChunks(seq, frameIndex, missing);
            return;
        }

        // ================= MSG =================
        if (header.type == 0x05) {

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;
                fwd.computeChecksum(payload);

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(fwd, payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            if (receivedHistory.isDuplicate(senderIp, senderPort, header.sequenceNumber)) {
                Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
                NodeContext.socket.sendPacket(ack, packet.getAddress(), hopPort);
                return;
            }

            String msg = new String(payload, StandardCharsets.UTF_8);
            System.out.println("MSG von " + IpUtil.intToIp(senderIp) + ":" + senderPort + " → " + msg);

            Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
            NodeContext.socket.sendPacket(ack, packet.getAddress(), hopPort);
            return;
        }

        // ================= FILE_CHUNK =================
        if (header.type == 0x06) {

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;
                fwd.computeChecksum(payload);

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(fwd, payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            ChunkAssembler.receiveChunk(header, payload);
            return;
        }

        // ================= FILE_INFO =================
        if (header.type == 0x07) {

            if (!isForMe) {
                PacketHeader fwd = header.copy();
                if (--fwd.ttl <= 0) return;
                fwd.computeChecksum(payload);

                Route r = RoutingTable.getRoute(destIp, destPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(fwd, payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            String filename = new String(payload, StandardCharsets.UTF_8);
            ChunkAssembler.setFileInfo(header, filename);

            Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
            NodeContext.socket.sendPacket(ack, packet.getAddress(), hopPort);
            return;
        }

        // ================= HEARTBEAT =================
        if (header.type == 0x08) {
            if (header.payloadLength != 0) return;
            RoutingTable.ensureDirectNeighbor(hopIp, hopPort);
            if (!wasAliveBefore) RoutingManager.broadcastRoutingUpdate();
            return;
        }

        // ================= ROUTING_UPDATE =================
        if (header.type == 0x09) {

            if (payload.length < 2) return;
            if (!NeighborManager.isAlive(hopIp, hopPort)) return;

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

                if (RoutingTable.learnFromUpdate(hopIp, hopPort, dip, dport, recvDist)) {
                    changed = true;
                }
            }

            if (changed) RoutingManager.broadcastRoutingUpdate();
        }
    }
}