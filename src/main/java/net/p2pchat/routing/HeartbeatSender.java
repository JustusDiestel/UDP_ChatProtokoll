package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PacketFactory;

public class HeartbeatSender {

    private static final long INTERVAL = 3000; // 3 sec

    public static void start() {

        Thread t = new Thread(() -> {

            while (true) {

                for (Neighbor n : NeighborManager.getAll().values()) {

                    if (!n.alive) continue;

                    int seq = NodeContext.seqGen.next();

                    Packet hb = PacketFactory.createHeartbeat(
                            seq,
                            n.ip,
                            n.port
                    );

                    NodeContext.socket.sendPacket(
                            hb,
                            NodeContext.socket.socketAddressForIp(n.ip),
                            n.port
                    );
                }

                try {
                    Thread.sleep(INTERVAL);
                } catch (InterruptedException ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }
}