package net.p2pchat.protocol;

import net.p2pchat.model.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingPackets {

    public static class Pending {
        public Packet packet;
        public long timestamp;
        public int attempts;
        public String ip;
        public int port;

        public Pending(Packet p, String ip, int port) {
            this.packet = p;
            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;
            this.ip = ip;
            this.port = port;
        }
    }

    private static final Map<Integer, Pending> pending = new ConcurrentHashMap<>();

    public static void track(Packet p, String ip, int port) {
        pending.put(p.header.sequenceNumber, new Pending(p, ip, port));
    }

    public static void clear(int seq) {
        pending.remove(seq);
    }

    public static Map<Integer, Pending> getPending() {
        return pending;
    }
}