package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;
import net.p2pchat.util.IpUtil;

import java.net.InetAddress;

public class HeartbeatSender {

    private static final long INTERVAL = 20_000; // 20 Sekunden

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {

                for (var entry : NeighborManager.getAll().entrySet()) {
                    Neighbor n = entry.getValue();

                    if (!n.alive)
                        continue;

                    try {
                        int seq = NodeContext.seqGen.next();

                        Packet hb = PacketFactory.createHeartbeat(
                                seq,
                                NodeContext.localIp,
                                n.ip
                        );

                        String ip = IpUtil.intToIp(n.ip);

                        NodeContext.socket.sendPacket(
                                hb,
                                InetAddress.getByName(ip),
                                n.port
                        );

                        System.out.println("HEART_BEAT → " + ip + ":" + n.port);

                    } catch (Exception e) {
                        System.err.println("Fehler beim Senden eines Heartbeats.");
                    }
                }

                try { Thread.sleep(INTERVAL); } catch (InterruptedException ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }
}