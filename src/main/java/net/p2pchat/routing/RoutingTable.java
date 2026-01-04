package net.p2pchat.routing;

import net.p2pchat.NodeContext;
import net.p2pchat.util.IpUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {

    private static final Map<String, Route> routes = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    // ============================================================
    // DIREKTEN NACHBARN SICHERSTELLEN (distance = 1)
    // ============================================================
    public static boolean ensureDirectNeighbor(int ip, int port) {

        if (ip == NodeContext.localIp && port == NodeContext.localPort)
            return false;

        String k = key(ip, port);
        Route old = routes.get(k);

        if (old != null && old.distance == 1)
            return false;

        routes.put(k, new Route(ip, port, ip, port, 1));
        return true;
    }

    // ============================================================
    // DISTANCE-VECTOR UPDATE
    // ============================================================
    public static boolean learnFromUpdate(
            int senderIp,
            int senderPort,
            int destIp,
            int destPort,
            int receivedDistance
    ) {
        // Eigene Adresse ignorieren
        if (destIp == NodeContext.localIp && destPort == NodeContext.localPort)
            return false;

        String k = key(destIp, destPort);
        Route old = routes.get(k);

        // =========================
        // POISON REVERSE
        // =========================
        if (receivedDistance >= 255) {

            if (old != null &&
                    old.nextHopIp == senderIp &&
                    old.nextHopPort == senderPort &&
                    old.distance != 1) {

                routes.remove(k);
                return true;
            }
            return false;
        }

        int newDist = Math.min(receivedDistance + 1, 255);
        Route candidate = new Route(destIp, destPort, senderIp, senderPort, newDist);

        // =========================
        // NEUE ROUTE
        // =========================
        if (old == null) {
            routes.put(k, candidate);
            return true;
        }

        // =========================
        // DIREKTE ROUTE SCHÜTZEN
        // =========================
        if (old.distance == 1)
            return false;

        // =========================
        // BESSERER WEG
        // =========================
        if (candidate.distance < old.distance) {
            routes.put(k, candidate);
            return true;
        }

        // =========================
        // GLEICHER NEXTHOP → UPDATE
        // =========================
        if (old.nextHopIp == senderIp && old.nextHopPort == senderPort) {
            old.distance = candidate.distance;
            return true;
        }

        return false;
    }

    // ============================================================
    // LOOKUP
    // ============================================================
    public static Route getRoute(int destIp, int destPort) {
        return routes.get(key(destIp, destPort));
    }

    // ============================================================
    // ROUTEN ÜBER AUSGEFALLENEN NACHBARN ENTFERNEN
    // ============================================================
    public static boolean removeVia(int nextHopIp, int nextHopPort) {
        int before = routes.size();

        routes.entrySet().removeIf(e -> {
            Route r = e.getValue();
            return r.nextHopIp == nextHopIp &&
                    r.nextHopPort == nextHopPort &&
                    r.distance != 1;
        });

        return routes.size() != before;
    }

    public static void removeDestination(int destIp, int destPort) {
        routes.remove(key(destIp, destPort));
    }

    public static Map<String, Route> getAll() {
        return routes;
    }

    public static void printTable(String prefix) {
        System.out.println("=== ROUTING TABLE (" + prefix + ") ===");
        for (Route r : routes.values()) {
            System.out.println(
                    "dest=" + IpUtil.intToIp(r.destIp) + ":" + r.destPort +
                            " -> nextHop=" + IpUtil.intToIp(r.nextHopIp) + ":" + r.nextHopPort +
                            " dist=" + r.distance
            );
        }
    }
}