package net.p2pchat.routing;

public class HeartbeatMonitor {

    private static final long TIMEOUT = 20_000; // 20 Sekunden

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {

                long now = System.currentTimeMillis();

                for (var entry : NeighborManager.getAll().entrySet()) {
                    Neighbor n = entry.getValue();

                    if (n.alive && (now - n.lastHeard > TIMEOUT)) {
                        n.alive = false;
                        System.out.println("Nachbar ausgefallen: " + n.ip + ":" + n.port);

                        // Routen entfernen
                        RoutingTable.removeVia(n.ip, n.port);


                    }
                }

                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }
}