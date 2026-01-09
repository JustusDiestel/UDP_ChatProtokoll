package net.p2pchat;

import net.p2pchat.file.FileSender;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.*;
import net.p2pchat.util.IpUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println(
                    "Usage: java -Dexec.mainClass=\"net.p2pchat.Main\" -Dexec.args=\"<PORT> [IP]\""
            );
            return;
        }

        // args: <PORT> [LOCAL_IP]
        NodeContext.localPort = Integer.parseInt(args[0]);

        if (args.length >= 2) {
            NodeContext.localIp = IpUtil.ipToInt(args[1]);
        } else {
            NodeContext.localIp = IpUtil.getLocalIpAsInt();
        }

        String localIpStr = IpUtil.intToIp(NodeContext.localIp);

        NodeContext.socket = new UdpSocket(NodeContext.localPort);
        NodeContext.socket.startReceiver();
        NodeContext.socket.startRetransmissionLoop();

        HeartbeatSender.start();
        HeartbeatMonitor.start();
        RoutingManager.startPeriodicUpdates();

        System.out.println("Node gestartet auf " + localIpStr + ":" + NodeContext.localPort);
        System.out.println("Befehle:");
        System.out.println("  connect <ip> <port>");
        System.out.println("  msg <ip> <port> <text>");
        System.out.println("  sendfile <ip> <port> <pfad>");
        System.out.println("  routes");
        System.out.println("  quit");
        System.out.println();

        Scanner sc = new Scanner(System.in);

        while (true) {

            System.out.print("> ");
            if (!sc.hasNextLine()) break;

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit")) break;

            if (line.equals("routes")) {
                RoutingTable.printTable(localIpStr + ":" + NodeContext.localPort);
                continue;
            }

            if (line.startsWith("connect ")) {

                String[] p = line.split(" ");
                if (p.length != 3) {
                    System.out.println("Usage: connect <ip> <port>");
                    continue;
                }

                String ip = p[1];
                int port = Integer.parseInt(p[2]);
                int destIp = IpUtil.ipToInt(ip);

                var hello = PacketFactory.createHello(
                        NodeContext.seqGen.next(),
                        destIp,
                        port
                );

                NodeContext.socket.sendPacket(
                        hello,
                        java.net.InetAddress.getByName(ip),
                        port
                );

                System.out.println("HELLO gesendet → " + ip + ":" + port);
                continue;
            }

            if (line.startsWith("msg ")) {

                String[] p = line.split(" ", 4);
                if (p.length < 4) {
                    System.out.println("Usage: msg <ip> <port> <text>");
                    continue;
                }

                int destIp = IpUtil.ipToInt(p[1]);
                int destPort = Integer.parseInt(p[2]);
                String text = p[3];

                RoutingManager.sendMsg(destIp, destPort, text);
                continue;
            }

            if (line.startsWith("sendfile ")) {

                String[] p = line.split(" ");
                if (p.length != 4) {
                    System.out.println("Usage: sendfile <ip> <port> <path>");
                    continue;
                }

                int destIp = IpUtil.ipToInt(p[1]);
                int destPort = Integer.parseInt(p[2]);
                String path = p[3];

                byte[] file;

                try {
                    file = Files.readAllBytes(Paths.get(path));
                } catch (IOException e) {
                    System.out.println("Kann Datei nicht lesen: " + e.getMessage());
                    continue;
                }

                final byte[] fileData = file;
                new Thread(() -> {
                    try {
                        FileSender.sendFile(fileData, destIp, destPort, path);
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
                System.out.println("File transfer started in background: " + path);
                continue;
            }

            System.out.println("Unbekannter Befehl.");
        }

        // ========================================================================
        // ⚠️ NEU: GOODBYE AN ALLE NACHBARN SENDEN (Spezifikation 4.3)
        // ========================================================================
        System.out.println("Stopping node...");
        System.out.println("Sending GOODBYE to all neighbors...");

        for (var entry : NeighborManager.getAll().entrySet()) {
            Neighbor n = entry.getValue();

            // Nur an lebende Nachbarn senden
            if (!n.alive) continue;

            try {
                var goodbye = PacketFactory.createGoodbye(
                        NodeContext.seqGen.next(),
                        n.ip,
                        n.port
                );

                NodeContext.socket.sendPacket(
                        goodbye,
                        java.net.InetAddress.getByName(IpUtil.intToIp(n.ip)),
                        n.port
                );

                System.out.println("GOODBYE → " + IpUtil.intToIp(n.ip) + ":" + n.port);

            } catch (Exception e) {
                System.err.println("Failed to send GOODBYE to " +
                        IpUtil.intToIp(n.ip) + ":" + n.port + " - " + e.getMessage());
            }
        }

        // Kurz warten, damit GOODBYE-Pakete noch gesendet werden können
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        NodeContext.socket.stop();
        System.out.println("Node stopped.");
    }
}