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
import java.util.Arrays;

public class PacketReceiver {

    private static final ReceivedHistory receivedHistory = new ReceivedHistory();

    public static void handle(DatagramPacket packet) {

        int len = packet.getLength();
        byte[] raw = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + len);
        PacketHeader header = PacketHeader.fromBytes(raw);

        NeighborManager.updateOrAdd(header.sourceIp, packet.getPort());

        if (header.type == 0x01) {
            System.out.println("ACK für seq=" + header.sequenceNumber + " empfangen.");
            PendingPackets.clear(header.sequenceNumber);
            return;
        }

        boolean duplicate = receivedHistory.isDuplicate(header.sourceIp, header.sequenceNumber);
        if (duplicate) {
            System.out.println("Duplikat seq=" + header.sequenceNumber + " → sende ACK erneut.");

            Packet ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    NodeContext.localIp,
                    header.sourceIp
            );
            NodeContext.socket.sendPacket(ack, packet.getAddress(), packet.getPort());
            return;
        }

        System.out.println("Neues Paket: type=" + header.type +
                ", seq=" + header.sequenceNumber +
                ", len=" + header.payloadLength);


        int headerSize = PacketHeader.HEADER_SIZE;
        if (raw.length < headerSize + header.payloadLength) {
            System.out.println("Warnung: Paket zu kurz.");
            return;
        }

        byte[] payload = Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);

        byte[] calc = HashUtil.sha256(payload);
        boolean valid = Arrays.equals(calc, header.checksum);

        System.out.println("Checksum valid: " + valid);

        if (!valid) {
            System.out.println("Ungültige Checksumme -> Paket verwerfen.");
            return;
        }

        // ROUTING_UPDATE
        if (header.type == 0x08) {
            System.out.println("ROUTING_UPDATE empfangen von " + header.sourceIp + ":" + packet.getPort());

            ByteBuffer buf = ByteBuffer.wrap(payload);

            int entryCount = buf.getShort() & 0xFFFF; // unsigned
            boolean tableChanged = false;

            int nextHopIp = header.sourceIp;
            int nextHopPort = packet.getPort();

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
                    continue;
                }
            }

            if (tableChanged) {
                System.out.println("Routing-Tabelle aktualisiert durch Update von " +
                        header.sourceIp + ":" + packet.getPort());
                // TODO: optional weiteres ROUTING_UPDATE an Nachbarn triggern
            }

            return;
        }

        // MSG
        if (header.type == 0x05) {

            // Wenn Nachricht nicht für uns ist → weiterleiten
            if (header.destinationIp != NodeContext.localIp) {

                System.out.println("MSG weiterleiten (Ziel " + header.destinationIp + ")");

                // TTL reduzieren
                header.ttl--;
                if (header.ttl <= 0) {
                    System.out.println("TTL abgelaufen. Paket verworfen.");
                    return;
                }

                // Route suchen
                Route r = RoutingTable.getRoute(header.destinationIp, packet.getPort());

                if (r == null) {
                    System.out.println("Keine Route → MSG verworfen.");
                    return;
                }

                String nextHop = IpUtil.intToIp(r.nextHopIp);

                // Weiterleiten (reliable)
                NodeContext.socket.sendReliable(
                        new Packet(header, payload),
                        nextHop,
                        r.nextHopPort
                );

                return;
            }

            System.out.println("MSG empfangen, sende ACK zurück.");

            Packet ack = PacketFactory.createAck(
                    header.sequenceNumber,
                    NodeContext.localIp,
                    header.sourceIp
            );

            NodeContext.socket.sendPacket(
                    ack,
                    packet.getAddress(),
                    packet.getPort()
            );

            return;
        }

        if (header.type == 0x03) { // HELLO
            System.out.println("HELLO empfangen von " + header.sourceIp + ":" + packet.getPort());

            // Direkte Route zum Nachbarn (Distance = 1)
            Route r = new Route(
                    header.sourceIp,
                    packet.getPort(),
                    header.sourceIp,      // Next-Hop ist direkt der Nachbar
                    packet.getPort(),
                    1
            );
            RoutingTable.addOrUpdate(r);

            return;
        }

        if (header.type == 0x04) { // GOODBYE
            System.out.println("GOODBYE empfangen von " + header.sourceIp + ":" + packet.getPort());

            NeighborManager.markDead(header.sourceIp, packet.getPort());
            RoutingTable.removeVia(header.sourceIp, packet.getPort());

            return;
        }

        // FILE_CHUNK
        if (header.type == 0x06) {
            System.out.println("FILE_CHUNK empfangen (Chunk " + header.chunkId + ")");
            // TODO: FileAssembler
            return;
        }

        // HEART_BEAT
        if (header.type == 0x07) {
            System.out.println("HEART_BEAT empfangen von " + header.sourceIp + ":" + packet.getPort());

            // NeighborManager.updateOrAdd() oben in Receiver aktualisiert lastHeard automatisch

            return;
        }

        // NO_ACK (Type 0x02) – zuerst behandeln, bevor FILE_CHUNK
        if (header.type == 0x02) {
            if (payload.length < 4) {
                System.out.println("NO_ACK Payload zu kurz.");
                return;
            }

            int missingChunkId =
                    ((payload[0] & 0xFF) << 24) |
                            ((payload[1] & 0xFF) << 16) |
                            ((payload[2] & 0xFF) << 8)  |
                            (payload[3] & 0xFF);

            System.out.println("NO_ACK empfangen – fehlender Chunk: " + missingChunkId);

            FileResender.resendChunk(
                    header.sourceIp,     // Empfänger der Datei ist der NO_ACK-Sender
                    packet.getPort(),
                    missingChunkId
            );
            return;
        }

        // FILE_CHUNK (Type 0x06)
        if (header.type == 0x06) {
            ChunkAssembler.receiveChunk(header, payload, packet.getPort());
            return;
        }
    }
}