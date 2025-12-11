package net.p2pchat.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {

    private static final Map<String, Route> routes = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    /**
     * Fügt eine Route hinzu oder aktualisiert sie.
     * DISTANCE-VECTOR Regel:
     * - Falls Eintrag neu → übernehmen
     * - Falls besserer Weg → übernehmen
     * - Falls gleicher NextHop → übernehmen
     */
    public static void addOrUpdate(Route r) {

        String k = key(r.destIp, r.destPort);
        Route old = routes.get(k);

        if (old == null) {
            routes.put(k, r);
            return;
        }

        // direkte Route (distance=1) NIE überschreiben
        if (old.distance == 1)
            return;

        // besserer Weg
        if (r.distance < old.distance) {
            routes.put(k, r);
            return;
        }

        // gleicher NextHop → Distance aktualisieren
        if (old.nextHopIp == r.nextHopIp && old.nextHopPort == r.nextHopPort) {
            routes.put(k, r);
        }
    }


    /**
     * Liefert die beste Route zum Ziel.
     */
    public static Route getRoute(int destIp, int destPort) {
        return routes.get(key(destIp, destPort));
    }


    /**
     * Entfernt alle Routen, deren nextHop der ausgefallene Nachbar ist.
     * Wird vom HeartbeatMonitor aufgerufen.
     */
    public static void removeVia(int nextHopIp, int nextHopPort) {
        routes.entrySet().removeIf(e -> {
            Route r = e.getValue();
            return r.nextHopIp == nextHopIp && r.nextHopPort == nextHopPort;
        });
    }


    /**
     * Entfernt genau EIN Ziel.
     */
    public static void removeDestination(int destIp, int destPort) {
        routes.remove(key(destIp, destPort));
    }


    /**
     * Gibt gesamte Tabelle zurück.
     */
    public static Map<String, Route> getAll() {
        return routes;
    }


    /**
     * Debug-Print.
     */
    public static void print() {
        System.out.println("RoutingTable:");
        for (Route r : routes.values()) {
            System.out.println(
                    "  dest=" + r.destIp + ":" + r.destPort +
                            " via=" + r.nextHopIp + ":" + r.nextHopPort +
                            " dist=" + r.distance
            );
        }
    }

    public static void printTable(String prefix) {
        System.out.println("=== ROUTING TABLE (" + prefix + ") ===");
        for (var e : routes.entrySet()) {
            Route r = e.getValue();
            System.out.println(
                    "dest=" + r.destIp + ":" + r.destPort +
                            " -> nextHop=" + r.nextHopIp + ":" + r.nextHopPort +
                            " dist=" + r.distance
            );
        }
    }
}