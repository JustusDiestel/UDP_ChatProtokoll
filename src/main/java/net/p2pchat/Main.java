package net.p2pchat;

import net.p2pchat.model.Packet;
import net.p2pchat.network.UdpSocket;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.routing.HeartbeatMonitor;
import net.p2pchat.routing.HeartbeatSender;

import java.net.InetAddress;

public class Main {

    public static void main(String[] args) throws Exception {

        int port = 5000;  // Port deines Knotens

        // ----------------------------
        // Node starten
        // ----------------------------

        NodeContext.socket = new UdpSocket(port);
        NodeContext.socket.startReceiver();
        NodeContext.socket.startRetransmissionLoop();

        HeartbeatSender.start();
        HeartbeatMonitor.start();

        System.out.println("Node gestartet auf Port " + port);
        System.out.println("Lokale IP (int): " + NodeContext.localIp);

        // ----------------------------
        // HELLO als Startsignal senden
        // (wird an dich selbst geschickt)
        // ----------------------------

        int seq = NodeContext.seqGen.next();

        Packet hello = PacketFactory.createHello(
                seq,
                NodeContext.localIp,   // sourceIp
                NodeContext.localIp    // destIp (self-test)
        );

        NodeContext.socket.sendPacket(
                hello,
                InetAddress.getByName("127.0.0.1"),
                port
        );

        System.out.println("HELLO gesendet (seq=" + seq + ")");

        // ----------------------------
        // Node läuft dauerhaft
        // ----------------------------

        System.out.println("Node läuft. Drücke Enter zum Beenden.");
        new java.util.Scanner(System.in).nextLine();

        System.out.println("Beende...");
        NodeContext.socket.stop();
    }
}