package net.p2pchat.routing;

public class Route {

    public int destIp;
    public int destPort;

    public int nextHopIp;
    public int nextHopPort;

    public int distance;
    public volatile long poisonedAt = 0;

    public Route(int destIp, int destPort, int nextHopIp, int nextHopPort, int distance) {
        this.destIp = destIp;
        this.destPort = destPort;
        this.nextHopIp = nextHopIp;
        this.nextHopPort = nextHopPort;
        this.distance = distance;
    }

    /**
     * Wichtig für RoutingUpdateUtil.
     * Erlaubt, das Objekt gefahrlos zu kopieren.
     */
    public Route copy() {
        Route r = new Route(destIp, destPort, nextHopIp, nextHopPort, distance);
        r.poisonedAt = this.poisonedAt;
        return r;
    }

    @Override
    public String toString() {
        return destIp + ":" + destPort +
                " via " + nextHopIp + ":" + nextHopPort +
                " dist=" + distance;
    }
}