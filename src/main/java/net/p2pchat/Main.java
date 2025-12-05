package net.p2pchat;

import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.util.SequenceNumberGenerator;

public class Main {
    private static final SequenceNumberGenerator SEQ = new SequenceNumberGenerator();

    public static void main(String[] args) throws Exception {
        int port = 5000;

        UdpSocket socket = new UdpSocket(port);
        socket.startReceiver();

        // Beispiel-Payload
        String msg = "TEST_DUP_CHECK";
        byte[] payload = msg.getBytes();

        PacketHeader header = new PacketHeader();
        header.type = 0x05; // MSG
        header.sequenceNumber = SEQ.next();
        header.sourceIp = 123;       // später: echte lokale IP als int
        header.destinationIp = 0;    // z.B. 0 bei Test
        header.payloadLength = payload.length;
        header.ttl = 10;
        header.computeChecksum(payload);

        Packet p = new Packet(header, payload);

        // zweimal senden mit der gleichen seq -> zweites Paket sollte als Duplikat erkannt werden
        socket.send(p.toBytes(), "127.0.0.1", port);
        socket.send(p.toBytes(), "127.0.0.1", port);

        System.out.println("Zwei Pakete mit gleicher SequenceNumber gesendet.");

        System.out.println("Knoten läuft. Enter zum Beenden.");
        new java.util.Scanner(System.in).nextLine();
    }
}