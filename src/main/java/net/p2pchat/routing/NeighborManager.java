package net.p2pchat.routing;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeighborManager {

    private static final Map<String, Neighbor> neighbors = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static boolean updateOrAdd(int ip, int port) {
        String k = key(ip, port);
        Neighbor before = neighbors.get(k);

        neighbors.compute(k, (__, old) -> {
            if (old == null) return new Neighbor(ip, port);
            old.updateLastHeard();
            return old;
        });

        Neighbor after = neighbors.get(k);
        if (before == null) {
            System.out.println("Neuer Nachbar: " + k);
            return true;
        }

        if (!before.alive && after.alive) {
            System.out.println("Nachbar wieder aktiv: " + k);
            RoutingTable.ensureDirectNeighbor(ip, port);
            RoutingManager.topologyChanged = true;
            return true;
        }

        return false;
    }

    public static void markDead(int ip, int port) {
        Neighbor n = neighbors.get(key(ip, port));
        if (n != null && n.alive) {
            n.markDead();
            System.out.println("Nachbar tot: " + ip + ":" + port);
        }
    }

    public static boolean isAlive(int ip, int port) {
        Neighbor n = neighbors.get(key(ip, port));
        return n != null && n.alive;
    }

    public static Collection<Neighbor> values() {
        return neighbors.values();
    }

    public static Map<String, Neighbor> getAll() {
        return neighbors;
    }
}