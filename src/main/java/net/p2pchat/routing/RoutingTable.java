package net.p2pchat.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {

    private static final Map<String, Route> routes = new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public static void addOrUpdate(Route r) {
        routes.put(key(r.destIp, r.destPort), r);
    }

    public static Route getRoute(int destIp, int destPort) {
        return routes.get(key(destIp, destPort));
    }

    public static Map<String, Route> getAll() {
        return routes;
    }

    public static void removeVia(int nextHopIp, int nextHopPort) {
        routes.entrySet().removeIf(e -> {
            Route r = e.getValue();
            return r.nextHopIp == nextHopIp && r.nextHopPort == nextHopPort;
        });
    }
}