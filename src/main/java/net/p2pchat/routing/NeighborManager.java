package net.p2pchat.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeighborManager {

    private static final Map<String, Neighbor> neighbors = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static void updateOrAdd(int ip, int port) {
        String k = key(ip, port);

        neighbors.compute(k, (__, old) -> {
            if (old == null) {
                System.out.println("Neuer Nachbar: " + k);
                return new Neighbor(ip, port);
            } else {
                old.updateLastHeard();
                return old;
            }
        });
    }

    public static void markDead(int ip, int port) {
        String k = key(ip, port);
        Neighbor n = neighbors.get(k);

        if (n != null) {
            n.alive = false;
            System.out.println("Nachbar tot: " + k);
        }
    }

    public static Map<String, Neighbor> getAll() {
        return neighbors;
    }
}