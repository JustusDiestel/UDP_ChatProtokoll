package net.p2pchat;

import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.network.UdpSocket;

public class Main {
    public static void main(String[] args) {
        int port = 5000;

        System.out.println("Starte P2P-Chat-Knoten auf Port " + port);

        UdpSocket socket = new UdpSocket(port);
        socket.startReceiver();

        System.out.println("Knoten läuft. Wartet auf UDP-Pakete...");

        String msg = "HELLO_FROM_JUSTUS";
        byte[] payload = msg.getBytes();

        PacketHeader header = new PacketHeader();
        header.type = 0x03; // HELLO
        header.sequenceNumber = 1;
        header.sourceIp = 0; // setzen wir später dynamisch
        header.destinationIp = 0; // HELLO ist broadcast
        header.payloadLength = payload.length;
        header.ttl = 10;

        header.computeChecksum(payload);

        Packet packet = new Packet(header, payload);

        socket.send(packet.toBytes(), "127.0.0.1", 5000);

        System.out.println("Testpaket gesendet.");
        new java.util.Scanner(System.in).nextLine();
    }
}