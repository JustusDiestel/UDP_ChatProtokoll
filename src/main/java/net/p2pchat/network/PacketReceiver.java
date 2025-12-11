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

        int len = packet.getLength();
        byte[] raw = Arrays.copyOfRange(packet.getData(),
                packet.getOffset(),
                packet.getOffset() + len);

        PacketHeader header = PacketHeader.fromBytes(raw);

        int senderIp   = header.sourceIp;
        int senderPort = header.destinationPort & 0xFFFF;

        int receiverIp   = header.destinationIp;
        int receiverPort = header.sourcePort & 0xFFFF;

        NeighborManager.updateOrAdd(senderIp, senderPort);

        int headerSize = PacketHeader.HEADER_SIZE;

        if (raw.length < headerSize + header.payloadLength) {
            System.out.println("WARN: Paket zu kurz");
            return;
        }

        byte[] payload =
                Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);

        boolean valid = Arrays.equals(HashUtil.sha256(payload), header.checksum);
        if (!valid) {
            System.out.println("Checksum FAILED");
            return;
        }

        // ============================================================
        // ACK
        // ============================================================
        if (header.type == 0x01) {
            PendingPackets.clear(header.sequenceNumber);
            return;
        }

        // ============================================================
        // NO_ACK
        // ============================================================
        if (header.type == 0x02) {

            ByteBuffer buf = ByteBuffer.wrap(payload);

            int frameSeq = buf.getInt();
            int missingCount = buf.getShort() & 0xFFFF;

            int[] missing = new int[missingCount];
            for (int i = 0; i < missingCount; i++)
                missing[i] = buf.getInt();

            PendingPackets.updateMissingChunks(frameSeq, missing);

            FileResender.resendChunks(senderIp, senderPort, frameSeq, missing);
            return;
        }

        // ============================================================
        // HELLO
        // ============================================================
        if (header.type == 0x03) {

            boolean isNew = !NeighborManager.isAlive(senderIp, senderPort);

            RoutingTable.addOrUpdate(new Route(senderIp, senderPort, senderIp, senderPort, 1));

            if (isNew) {
                RoutingManager.broadcastRoutingUpdate();
            }

            return;
        }

        // ============================================================
        // GOODBYE
        // ============================================================
        if (header.type == 0x04) {

            NeighborManager.markDead(senderIp, senderPort);
            RoutingTable.removeVia(senderIp, senderPort);
            RoutingManager.broadcastRoutingUpdate();
            return;
        }

        // ============================================================
        // MSG
        // ============================================================
        if (header.type == 0x05) {

            boolean isForMe =
                    receiverIp == NodeContext.localIp &&
                            receiverPort == NodeContext.localPort;

            if (!isForMe) {

                header.ttl--;
                if (header.ttl <= 0) return;

                Route r = RoutingTable.getRoute(receiverIp, receiverPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(header.copy(), payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            if (receivedHistory.isDuplicate(senderIp, header.sequenceNumber)) {
                Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
                NodeContext.socket.sendPacket(ack,
                        packet.getAddress(), senderPort);
                return;
            }

            String msg = new String(payload, StandardCharsets.UTF_8);
            System.out.println("MSG von " + IpUtil.intToIp(senderIp) + ":" + senderPort + " → " + msg);

            Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
            NodeContext.socket.sendPacket(ack,
                    packet.getAddress(), senderPort);

            return;
        }

        // ============================================================
        // FILE_CHUNK
        // ============================================================
        if (header.type == 0x06) {

            boolean isForMe =
                    receiverIp == NodeContext.localIp &&
                            receiverPort == NodeContext.localPort;

            if (!isForMe) {

                header.ttl--;
                if (header.ttl <= 0) return;

                Route r = RoutingTable.getRoute(receiverIp, receiverPort);
                if (r == null) return;

                NodeContext.socket.sendPacket(
                        new Packet(header.copy(), payload),
                        NodeContext.socket.socketAddressForIp(r.nextHopIp),
                        r.nextHopPort
                );
                return;
            }

            ChunkAssembler.receiveChunk(header, payload);
            return;
        }

        // ============================================================
        // FILE_INFO
        // ============================================================
        if (header.type == 0x07) {

            if (receivedHistory.isDuplicate(senderIp, header.sequenceNumber)) {
                Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
                NodeContext.socket.sendPacket(ack,
                        packet.getAddress(), senderPort);
                return;
            }

            ByteBuffer buf = ByteBuffer.wrap(payload);

            int totalChunks = buf.getInt();
            int nameLen = buf.getShort() & 0xFFFF;

            byte[] nameBytes = new byte[nameLen];
            buf.get(nameBytes);

            String filename = new String(nameBytes, StandardCharsets.UTF_8);

            ChunkAssembler.setFileInfo(header, totalChunks, filename);

            Packet ack = PacketFactory.createAck(header.sequenceNumber, senderIp, senderPort);
            NodeContext.socket.sendPacket(ack,
                    packet.getAddress(), senderPort);

            return;
        }

        // ============================================================
        // HEARTBEAT
        // ============================================================
        if (header.type == 0x08) {

            Route direct = RoutingTable.getRoute(senderIp, senderPort);

            if (direct == null || direct.distance > 1) {
                RoutingTable.addOrUpdate(new Route(senderIp, senderPort, senderIp, senderPort, 1));
                RoutingManager.broadcastRoutingUpdate();
            }

            return;
        }

        // ============================================================
        // ROUTING_UPDATE
        // ============================================================
        if (header.type == 0x09) {

            ByteBuffer buf = ByteBuffer.wrap(payload);

            int entryCount = buf.getShort() & 0xFFFF;

            int nextHopIp   = senderIp;
            int nextHopPort = senderPort;

            boolean changed = false;

            for (int i = 0; i < entryCount; i++) {

                int destIp   = buf.getInt();
                int destPort = buf.getShort() & 0xFFFF;
                int recvDist = buf.get() & 0xFF;

                int newDist = recvDist + 1;

                if (destIp == NodeContext.localIp &&
                        destPort == NodeContext.localPort)
                    continue;

                Route existing = RoutingTable.getRoute(destIp, destPort);

                if (existing == null) {
                    RoutingTable.addOrUpdate(
                            new Route(destIp, destPort, nextHopIp, nextHopPort, newDist));
                    changed = true;
                    continue;
                }

                if (existing.distance == 1) continue;

                if (newDist < existing.distance) {
                    existing.distance = newDist;
                    existing.nextHopIp = nextHopIp;
                    existing.nextHopPort = nextHopPort;
                    changed = true;
                    continue;
                }

                if (existing.nextHopIp == nextHopIp &&
                        existing.nextHopPort == nextHopPort) {
                    existing.distance = newDist;
                    changed = true;
                }
            }

            if (changed) RoutingManager.broadcastRoutingUpdate();
        }
    }
}