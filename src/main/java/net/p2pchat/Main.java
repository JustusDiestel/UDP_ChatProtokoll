package net.p2pchat;

import net.p2pchat.file.FileSender;
import net.p2pchat.model.Packet;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.RoutingManager;
import net.p2pchat.util.IpUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length < 1) return;

        int localPort = Integer.parseInt(args[0]);
        NodeContext.localPort = localPort;

        NodeContext.socket = new UdpSocket(localPort);
        NodeContext.socket.startReceiver();
        NodeContext.socket.startRetransmissionLoop();

        String localIpStr = IpUtil.intToIp(NodeContext.localIp);

        System.out.println("Node gestartet auf " + localIpStr + ":" + localPort);
        System.out.println("Befehle:");
        System.out.println("  connect <ip> <port>");
        System.out.println("  msg <ip> <port> <text>");
        System.out.println("  sendfile <ip> <port> <pfad>");
        System.out.println("  quit");
        System.out.println();

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit")) break;

            if (line.startsWith("connect ")) {
                String[] p = line.split(" ");
                if (p.length != 3) continue;

                String ip = p[1];
                int port = Integer.parseInt(p[2]);

                var hello = PacketFactory.createHello(
                        NodeContext.seqGen.next(),
                        IpUtil.ipToInt(ip),
                        port
                );

                NodeContext.socket.sendPacket(
                        hello,
                        java.net.InetAddress.getByName(ip),
                        port
                );

                continue;
            }

            if (line.startsWith("msg ")) {
                String[] p = line.split(" ", 4);
                if (p.length < 4) continue;

                String ip = p[1];
                int port = Integer.parseInt(p[2]);
                String text = p[3];

                int destIp = IpUtil.ipToInt(ip);

                RoutingManager.sendMsg(destIp, port, text);
                continue;
            }

            if (line.startsWith("sendfile ")) {
                String[] p = line.split(" ");
                if (p.length != 4) continue;

                String ip = p[1];
                int port = Integer.parseInt(p[2]);
                String path = p[3];

                byte[] file;
                try {
                    file = Files.readAllBytes(Paths.get(path));
                } catch (IOException e) {
                    System.out.println("Kann Datei nicht lesen.");
                    continue;
                }

                int destIp = IpUtil.ipToInt(ip);

                FileSender.sendFile(file, destIp, port);
                continue;
            }

            System.out.println("Unbekannter Befehl.");
        }

        NodeContext.socket.stop();
    }
}