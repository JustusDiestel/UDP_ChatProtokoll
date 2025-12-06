package net.p2pchat.routing;

public class Route {

    public int destIp;
    public int destPort;
    public int nextHopIp;
    public int nextHopPort;
    public int distance;

    public Route(int destIp, int destPort, int nextHopIp, int nextHopPort, int distance) {
        this.destIp = destIp;
        this.destPort = destPort;
        this.nextHopIp = nextHopIp;
        this.nextHopPort = nextHopPort;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return destIp + ":" + destPort +
                " via " + nextHopIp + ":" + nextHopPort +
                " dist=" + distance;
    }
}