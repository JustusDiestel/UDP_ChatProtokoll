package net.p2pchat.routing;

public class Neighbor {

    public final int ip;
    public final int port;

    public volatile long lastHeard;
    public volatile boolean alive;

    public Neighbor(int ip, int port) {
        this.ip = ip;
        this.port = port;
        this.lastHeard = System.currentTimeMillis();
        this.alive = true;
    }

    public void updateLastHeard() {
        this.lastHeard = System.currentTimeMillis();
        this.alive = true;
    }

    public void markDead() {
        this.alive = false;
    }
}