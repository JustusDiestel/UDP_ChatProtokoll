package net.p2pchat.routing;

public class HeartbeatMonitor {

    // Spezifikation: Timeout = 2 * HB-Interval + 1000 ms
    private static final long HEARTBEAT_INTERVAL = 5000;               // 5 Sekunden
    private static final long TIMEOUT = HEARTBEAT_INTERVAL * 2 + 1000; // 11000 ms

    public static void start() {

        Thread t = new Thread(() -> {

            while (true) {

                long now = System.currentTimeMillis();

                for (var entry : NeighborManager.getAll().entrySet()) {

                    Neighbor n = entry.getValue();

                    // Nachbar war lebend, ist jetzt aber timeoutet
                    if (n.alive && (now - n.lastHeard > TIMEOUT)) {

                        n.alive = false;

                        System.out.println(
                                "[HEARTBEAT] Nachbar ausgefallen: "
                                        + n.ip + ":" + n.port
                                        + " | lastHeard=" + (now - n.lastHeard) + "ms"
                        );

                        // ===== WICHTIGER FIX =====

                        // 1. direkte Route (dist = 1) entfernen
                        RoutingTable.removeDestination(n.ip, n.port);

                        // 2. alle indirekten Routen über diesen Nachbarn entfernen
                        boolean changed = RoutingTable.removeVia(n.ip, n.port);

                        // 3. Routing-Update nur senden, wenn sich etwas geändert hat
                        if (changed) {
                            RoutingManager.broadcastRoutingUpdate();
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }

        });

        t.setDaemon(true);
        t.start();
    }
}