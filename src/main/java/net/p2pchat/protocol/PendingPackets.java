package net.p2pchat.protocol;

import net.p2pchat.model.Packet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingPackets {

    // ============================================================
    // Datenstruktur eines Pending-Eintrags
    // ============================================================
    public static class Pending {

        // FRAME-MODUS (FileTransfer)
        public Packet[] frameChunks;          // alle Chunks eines Frames
        public int frameSeq;                  // sequenceNumber des ERSTEN Chunks
        public int[] missingChunks;           // vom NO_ACK gesetzt

        // SINGLE-PACKET-MODUS (MSG, FILE_INFO)
        public Packet singlePacket;

        // Gemeinsame Infos
        public long timestamp;
        public int attempts;

        public int destIp;
        public int destPort;

        public boolean isFrame;

        // ========================================================
        // Constructor SINGLE PACKET
        // ========================================================
        public Pending(Packet p, int destIp, int destPort) {
            this.singlePacket = p;
            this.isFrame = false;

            this.destIp = destIp;
            this.destPort = destPort;

            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;
        }

        // ========================================================
        // Constructor FRAME
        // ========================================================
        public Pending(Packet[] frameChunks, int frameSeq, int destIp, int destPort) {

            this.frameChunks = frameChunks;
            this.frameSeq = frameSeq;

            this.isFrame = true;

            this.destIp = destIp;
            this.destPort = destPort;

            this.timestamp = System.currentTimeMillis();
            this.attempts = 1;

            this.missingChunks = null;
        }
    }

    // ============================================================
    // ZENTRALE Pending-Liste:
    //
    // key = sequenceNumber ODER frameSeq
    // ============================================================
    private static final Map<Integer, Pending> pending = new ConcurrentHashMap<>();


    // ============================================================
    // TRACK SINGLE PACKET (MSG, FILE_INFO)
    // ============================================================
    public static void trackSingle(Packet p, int destIp, int destPort) {
        pending.put(
                p.header.sequenceNumber,
                new Pending(p, destIp, destPort)
        );
    }


    // ============================================================
    // TRACK FRAME (StartSeq + 128 Chunks)
    // ============================================================
    public static void trackFrame(Packet[] frameChunks, int frameSeq, int destIp, int destPort) {
        pending.put(
                frameSeq,
                new Pending(frameChunks, frameSeq, destIp, destPort)
        );
    }


    // ============================================================
    // ACK löschen
    // ============================================================
    public static void clear(int seq) {
        pending.remove(seq);
    }


    // ============================================================
    // NO_ACK → fehlende Chunks setzen + Timer resetten
    // ============================================================
    public static void updateMissingChunks(int frameSeq, int[] missing) {

        Pending p = pending.get(frameSeq);
        if (p == null || !p.isFrame) return;

        p.missingChunks = missing;
        p.timestamp = System.currentTimeMillis();  // wichtig
    }


    // ============================================================
    // Zugriff
    // ============================================================
    public static Map<Integer, Pending> getPending() {
        return pending;
    }
}