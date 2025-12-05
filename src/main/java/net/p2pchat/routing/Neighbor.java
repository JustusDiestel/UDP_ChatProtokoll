package net.p2pchat.routing;

public class Neighbor {
    public int ip;
    public int port;
    public long lastHeard;
    public boolean alive;

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
}