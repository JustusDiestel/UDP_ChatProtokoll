package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;

public class HeartbeatSender {

    private static final long INTERVAL = 5000; // 5 Sekunden laut Spezifikation

    public static void start() {

        Thread t = new Thread(() -> {

            while (true) {

                for (var entry : NeighborManager.getAliveNeighbors().entrySet()) {

                    Neighbor n = entry.getValue();

                    try {
                        int seq = NodeContext.seqGen.next();

                        // HEARTBEAT-Paket ERZEUGEN (korrekte Header-Felder in PacketFactory!)
                        Packet hb = PacketFactory.createHeartbeat(
                                seq,
                                n.ip,
                                n.port
                        );

                        InetAddress addr = InetAddress.getByName(
                                IpUtil.intToIp(n.ip)
                        );

                        NodeContext.socket.sendPacket(hb, addr, n.port);

                    } catch (Exception e) {
                        System.err.println("Fehler beim Senden eines Heartbeats.");
                    }
                }

                try { Thread.sleep(INTERVAL); }
                catch (InterruptedException ignored) {}
            }

        });

        t.setDaemon(true);
        t.start();
    }
}