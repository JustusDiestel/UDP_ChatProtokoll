package net.p2pchat;

import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Usage: java net.p2pchat.Main <localPort>");
            return;
        }

        int localPort = Integer.parseInt(args[0]);

        // Socket + NodeContext initialisieren
        NodeContext.socket = new UdpSocket(localPort);
        NodeContext.socket.startReceiver();
        NodeContext.socket.startRetransmissionLoop();

        String localIpStr = IpUtil.intToIp(NodeContext.localIp);
        System.out.println("Node gestartet auf " + localIpStr + ":" + localPort);
        System.out.println("Befehle:");
        System.out.println("  msg <ip> <port> <text>");
        System.out.println("  quit");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }

            // Nachricht senden: msg <ip> <port> <text...>
            if (line.startsWith("msg ")) {
                String[] parts = line.split(" ", 4);
                if (parts.length < 4) {
                    System.out.println("Syntax: msg <ip> <port> <text>");
                    continue;
                }

                String destIpStr = parts[1];
                int destPort;
                try {
                    destPort = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    System.out.println("Ungültiger Port.");
                    continue;
                }
                String text = parts[3];

                byte[] payload = text.getBytes(StandardCharsets.UTF_8);

                PacketHeader header = new PacketHeader();
                header.type = 0x05; // MSG
                header.sequenceNumber = NodeContext.seqGen.next();
                header.sourceIp = NodeContext.localIp;
                // bei lokalen Tests: alle Knoten teilen sich dieselbe IP (127.0.0.1 / deine LAN-IP)
                header.destinationIp = NodeContext.localIp;
                header.payloadLength = payload.length;
                header.ttl = 10;
                header.computeChecksum(payload);

                Packet p = new Packet(header, payload);

                // zuverlässig senden (du brauchst in UdpSocket: sendReliable(Packet, String, int))
                NodeContext.socket.sendReliable(p, destIpStr, destPort);

                System.out.println("MSG gesendet an " + destIpStr + ":" + destPort);
                continue;
            }

            System.out.println("Unbekannter Befehl.");
        }

        System.out.println("Beende Node...");
        NodeContext.socket.stop();
    }
}