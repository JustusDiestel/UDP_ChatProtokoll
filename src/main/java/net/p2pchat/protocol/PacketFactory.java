package net.p2pchat.protocol;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PacketFactory {

    // ============================================================
    // BASISHEADER gemäß SPEZIFIKATION
    // ============================================================
    private static PacketHeader base(byte type, int seq, int destIp, int destPort, int payloadLen) {

        PacketHeader h = new PacketHeader();

        h.type = type;
        h.sequenceNumber = seq;

        // -----------------------------
        // Reihenfolge laut Header:
        // destinationIp
        // sourceIp
        // destinationPort (senderPort)
        // sourcePort      (receiverPort)
        // -----------------------------

        h.destinationIp   = destIp;                // final receiver
        h.sourceIp        = NodeContext.localIp;   // sender

        h.destinationPort = (short) NodeContext.localPort; // senderPort
        h.sourcePort      = (short) destPort;              // receiverPort

        h.payloadLength = payloadLen;

        h.chunkId = 0;
        h.chunkLength = 0;

        h.ttl = 64;

        return h;
    }


    // ============================================================
    // 0x01 ACK
    // ============================================================
    public static Packet createAck(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];

        PacketHeader h = base((byte)0x01, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x02 NO_ACK
    // ============================================================
    public static Packet createNoAck(int frameSeq, int destIp, int destPort, int[] missing) {

        ByteBuffer buf = ByteBuffer.allocate(4 + 2 + missing.length * 4);
        buf.putInt(frameSeq);
        buf.putShort((short) missing.length);

        for (int x : missing)
            buf.putInt(x);

        byte[] payload = buf.array();

        PacketHeader h = base((byte)0x02, frameSeq, destIp, destPort, payload.length);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x03 HELLO
    // ============================================================
    public static Packet createHello(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x03, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x04 GOODBYE
    // ============================================================
    public static Packet createGoodbye(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x04, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x05 MSG
    // ============================================================
    public static Packet createMessage(int seq, int destIp, int destPort, String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        PacketHeader h = base((byte)0x05, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x06 FILE_CHUNK
    // ============================================================
    public static Packet createFileChunk(int seq, int destIp, int destPort,
                                         int chunkId, int totalChunks, byte[] data) {

        PacketHeader h = base((byte)0x06, seq, destIp, destPort, data.length);

        h.chunkId = chunkId;
        h.chunkLength = totalChunks;

        h.computeChecksum(data);
        return new Packet(h, data);
    }


    // ============================================================
    // 0x07 FILE_INFO
    // ============================================================
    public static Packet createFileInfo(int seq, int destIp, int destPort,
                                        int totalChunks, String filename) {

        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = ByteBuffer.allocate(4 + 2 + nameBytes.length);
        buf.putInt(totalChunks);
        buf.putShort((short) nameBytes.length);
        buf.put(nameBytes);

        byte[] payload = buf.array();

        PacketHeader h = base((byte)0x07, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);

        return new Packet(h, payload);
    }


    // ============================================================
    // 0x08 HEARTBEAT
    // ============================================================
    public static Packet createHeartbeat(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x08, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }


    // ============================================================
    // 0x09 ROUTING_UPDATE
    // ============================================================
    public static Packet createRoutingUpdate(int seq, int destIp, int destPort, byte[] payload) {

        PacketHeader h = base((byte)0x09, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);

        return new Packet(h, payload);
    }
}