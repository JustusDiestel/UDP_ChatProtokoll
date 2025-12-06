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
        byte[] raw = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + len);
        PacketHeader header = PacketHeader.fromBytes(raw);

        NeighborManager.updateOrAdd(header.sourceIp, header.sourcePort & 0xFFFF);

        if (header.type == 0x01) {
            System.out.println("ACK für seq=" + header.sequenceNumber + " empfangen.");
            PendingPackets.clear(header.sequenceNumber);
            return;
        }

        int headerSize = PacketHeader.HEADER_SIZE;
        if (raw.length < headerSize + header.payloadLength) {
            System.out.println("Warnung: Paket zu kurz.");
            return;
        }

        byte[] payload = Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);

        byte[] calc = HashUtil.sha256(payload);
        boolean valid = Arrays.equals(calc, header.checksum);

        System.out.println("Neues Paket: type=" + header.type +
                ", seq=" + header.sequenceNumber +
                ", len=" + header.payloadLength +
                ", checksumValid=" + valid);

        if (!valid) return;

        if (header.type == 0x08) {
            ByteBuffer buf = ByteBuffer.wrap(payload);

            int entryCount = buf.getShort() & 0xFFFF;
            boolean tableChanged = false;

            int nextHopIp = header.sourceIp;
            int nextHopPort = header.sourcePort & 0xFFFF;

            for (int i = 0; i < entryCount; i++) {
                int destIp = buf.getInt();
                int destPort = buf.getShort() & 0xFFFF;
                int receivedDistance = buf.get() & 0xFF;

                int newDistance = receivedDistance + 1;

                Route existing = RoutingTable.getRoute(destIp, destPort);

                if (existing == null) {
                    Route r = new Route(destIp, destPort, nextHopIp, nextHopPort, newDistance);
                    RoutingTable.addOrUpdate(r);
                    tableChanged = true;
                    continue;
                }

                if (newDistance < existing.distance) {
                    existing.nextHopIp = nextHopIp;
                    existing.nextHopPort = nextHopPort;
                    existing.distance = newDistance;
                    tableChanged = true;
                    continue;
                }

                if (existing.nextHopIp == nextHopIp && existing.nextHopPort == nextHopPort) {
                    existing.distance = newDistance;
                    tableChanged = true;
                }
            }

            if (tableChanged) {
                System.out.println("Routing-Tabelle aktualisiert durch Update von " +
                        IpUtil.intToIp(header.sourceIp) + ":" + (header.sourcePort & 0xFFFF));
            }

            return;
        }

        if (header.type == 0x03) {

            int srcIp = header.sourceIp;
            int srcPort = header.sourcePort & 0xFFFF;

            Route direct = RoutingTable.getRoute(srcIp, srcPort);
            boolean isNewNeighbor = (direct == null || direct.distance > 1);

            Route r = new Route(
                    srcIp,
                    srcPort,
                    srcIp,
                    srcPort,
                    1
            );
            RoutingTable.addOrUpdate(r);
            NeighborManager.updateOrAdd(srcIp, srcPort);

            if (isNewNeighbor) {
                var reply = PacketFactory.createHello(
                        NodeContext.seqGen.next(),
                        srcIp,
                        srcPort
                );

                NodeContext.socket.sendPacket(
                        reply,
                        packet.getAddress(),
                        srcPort
                );
            }

            return;
        }

        if (header.type == 0x04) {
            NeighborManager.markDead(header.sourceIp, header.sourcePort & 0xFFFF);
            RoutingTable.removeVia(header.sourceIp, header.sourcePort & 0xFFFF);
            return;
        }

        if (header.type == 0x07) {
            return;
        }

        if (header.type == 0x02) {
            if (payload.length < 2) return;

            ByteBuffer buf = ByteBuffer.wrap(payload);
            int count = buf.getShort() & 0xFFFF;

            if (payload.length < 2 + count * 4) return;

            int[] missing = new int[count];
            for (int i = 0; i < count; i++) {
                missing[i] = buf.getInt();
            }

            FileResender.resendChunks(
                    header.sourceIp,
                    header.sourcePort & 0xFFFF,
                    missing
            );
            return;
        }

        if (header.type == 0x05 || header.type == 0x06) {
            boolean duplicate = receivedHistory.isDuplicate(header.sourceIp, header.sequenceNumber);
            if (duplicate) {
                if (header.type == 0x05) {
                    Packet ack = PacketFactory.createAck(
                            header.sequenceNumber,
                            header.sourceIp,
                            header.sourcePort & 0xFFFF
                    );
                    NodeContext.socket.sendPacket(ack, packet.getAddress(), header.sourcePort & 0xFFFF);
                }
                return;
            }
        }

        if (header.type == 0x05) {

            if (header.destinationIp != NodeContext.localIp ||
                    (header.destinationPort & 0xFFFF) != NodeContext.localPort) {

                header.ttl--;
                if (header.ttl <= 0) return;

                Route r = RoutingTable.getRoute(header.destinationIp, header.destinationPort & 0xFFFF);
                if (r == null) return;

                String nextHop = IpUtil.intToIp(r.nextHopIp);

                NodeContext.socket.sendReliable(
                        new Packet(header, payload),
                        nextHop,
                        r.nextHopPort
                );

                return;
            }

            String text = new String(payload, StandardCharsets.UTF_8);
            System.out.println("MSG von " +
                    IpUtil.intToIp(header.sourceIp) + ":" + (header.sourcePort & 0xFFFF) +
                    " → " + text);

            Packet ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    header.sourceIp,
                    header.sourcePort & 0xFFFF
            );

            NodeContext.socket.sendPacket(
                    ack,
                    packet.getAddress(),
                    header.sourcePort & 0xFFFF
            );

            return;
        }

        if (header.type == 0x06) {
            ChunkAssembler.receiveChunk(header, payload, header.sourcePort & 0xFFFF);
        }
    }
}