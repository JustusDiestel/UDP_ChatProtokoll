package net.p2pchat.protocol;

import net.p2pchat.model.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingPackets {

    public static class Pending {
        public Packet packet;
        public long timestamp;
        public int attempts;
        public int destIp;
        public int destPort;

        public Pending(Packet p, int destIp, int destPort) {
            this.packet = p;
            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;
            this.destIp = destIp;
            this.destPort = destPort;
        }
    }

    private static final Map<Integer, Pending> pending = new ConcurrentHashMap<>();

    public static void track(Packet p, int destIp, int destPort) {
        pending.put(p.header.sequenceNumber, new Pending(p, destIp, destPort));
    }

    public static void clear(int seq) {
        pending.remove(seq);
    }

    public static Map<Integer, Pending> getPending() {
        return pending;
    }
}