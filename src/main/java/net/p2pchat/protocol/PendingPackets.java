package net.p2pchat.protocol;

import net.p2pchat.model.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingPackets {

    public static class Pending {

        // FRAME
        public Packet[] frameChunks;
        public int sequenceNumber;   // Datei-ID
        public int frameIndex;       // chunkId / 128
        public int[] missingChunks;

        // SINGLE
        public Packet singlePacket;

        // META
        public long timestamp;
        public int attempts;
        public int destIp;
        public int destPort;
        public boolean isFrame;

        // SINGLE
        public Pending(Packet p, int destIp, int destPort) {
            this.singlePacket = p;
            this.sequenceNumber = p.header.sequenceNumber;
            this.frameIndex = -1;
            this.destIp = destIp;
            this.destPort = destPort;
            this.isFrame = false;
            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;
        }

        // FRAME
        public Pending(Packet[] chunks, int seq, int frameIndex, int destIp, int destPort) {
            this.frameChunks = chunks;
            this.sequenceNumber = seq;
            this.frameIndex = frameIndex;
            this.destIp = destIp;
            this.destPort = destPort;
            this.isFrame = true;
            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;
        }
    }

    private static long key(int seq, int frameIndex) {
        return (((long) seq) << 32) | (frameIndex & 0xffffffffL);
    }

    private static final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    // ================= SINGLE =================
    public static void trackSingle(Packet p, int destIp, int destPort) {
        pending.put(key(p.header.sequenceNumber, -1),
                new Pending(p, destIp, destPort));
    }

    public static void clearSingle(int sequenceNumber) {
        pending.remove(key(sequenceNumber, -1));
    }

    // ================= FRAME =================
    public static void trackFrame(Packet[] frameChunks,
                                  int sequenceNumber,
                                  int frameIndex,
                                  int destIp,
                                  int destPort) {

        pending.put(key(sequenceNumber, frameIndex),
                new Pending(frameChunks, sequenceNumber, frameIndex, destIp, destPort));
    }

    public static void clearFrame(int sequenceNumber, int frameIndex) {
        pending.remove(key(sequenceNumber, frameIndex));
    }

    public static void updateMissingChunks(int sequenceNumber,
                                           int frameIndex,
                                           int[] missing) {

        Pending p = pending.get(key(sequenceNumber, frameIndex));
        if (p == null || !p.isFrame) return;

        p.missingChunks = missing;
        p.timestamp = System.currentTimeMillis();
    }

    public static Map<Long, Pending> getPending() {
        return pending;
    }
}