package net.p2pchat.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeighborManager {

    private static final Map<String, Neighbor> neighbors = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    /**
     * Wird bei JEDEM eingehenden Paket aufgerufen.
     * Dient als Heartbeat-Ersatz (Erreichen des Nachbarn).
     */
    public static void updateOrAdd(int ip, int port) {
        String k = key(ip, port);

        neighbors.compute(k, (__, old) -> {
            if (old == null) {
                System.out.println("Neuer Nachbar: " + k);
                return new Neighbor(ip, port);
            }

            if (!old.alive) {
                System.out.println("Nachbar wieder aktiv: " + k);
                old.alive = true;
            }

            old.updateLastHeard();
            return old;
        });
    }

    /**
     * Vom HeartbeatMonitor benutzt.
     */
    public static void markDead(int ip, int port) {
        String k = key(ip, port);
        Neighbor n = neighbors.get(k);

        if (n != null && n.alive) {
            n.markDead();
            System.out.println("Nachbar tot: " + k);
        }
    }

    public static boolean isNeighbor(int ip, int port) {
        return neighbors.containsKey(key(ip, port));
    }

    public static boolean isAlive(int ip, int port) {
        Neighbor n = neighbors.get(key(ip, port));
        return n != null && n.alive;
    }

    public static Map<String, Neighbor> getAll() {
        return neighbors;
    }

    public static Map<String, Neighbor> getAliveNeighbors() {
        Map<String, Neighbor> alive = new ConcurrentHashMap<>();
        for (var e : neighbors.entrySet()) {
            if (e.getValue().alive) alive.put(e.getKey(), e.getValue());
        }
        return alive;
    }
}