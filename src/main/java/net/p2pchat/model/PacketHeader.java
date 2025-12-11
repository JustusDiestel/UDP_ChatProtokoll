package net.p2pchat.model;

import net.p2pchat.util.HashUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PacketHeader {

    public byte type;               // 1 byte
    public int sequenceNumber;      // 4 bytes

    // Reihenfolge exakt wie im Protokoll
    public int destinationIp;       // 4 bytes
    public int sourceIp;            // 4 bytes

    public short destinationPort;   // 2 bytes (final destination port)
    public short sourcePort;        // 2 bytes (original sender port)

    public int payloadLength;       // 4 bytes
    public int chunkId;             // 4 bytes
    public int chunkLength;         // 4 bytes

    public byte ttl;                // 1 byte

    public byte[] checksum = new byte[32]; // SHA-256 checksum (32 bytes)

    public static final int HEADER_SIZE =
            1 +        // type
                    4 +        // sequenceNumber
                    4 +        // destinationIp
                    4 +        // sourceIp
                    2 +        // destinationPort
                    2 +        // sourcePort
                    4 +        // payloadLength
                    4 +        // chunkId
                    4 +        // chunkLength
                    1 +        // ttl
                    32;        // checksum


    // SERIALIZE ===============================================================
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);

        buf.put(type);
        buf.putInt(sequenceNumber);

        buf.putInt(destinationIp);
        buf.putInt(sourceIp);

        buf.putShort(destinationPort);
        buf.putShort(sourcePort);

        buf.putInt(payloadLength);
        buf.putInt(chunkId);
        buf.putInt(chunkLength);

        buf.put(ttl);
        buf.put(checksum);

        return buf.array();
    }


    // DESERIALIZE =============================================================
    public static PacketHeader fromBytes(byte[] bytes) {
        if (bytes.length < HEADER_SIZE)
            throw new IllegalArgumentException("Header zu klein: " + bytes.length);

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        PacketHeader h = new PacketHeader();

        h.type = buf.get();
        h.sequenceNumber = buf.getInt();

        h.destinationIp = buf.getInt();
        h.sourceIp = buf.getInt();

        h.destinationPort = buf.getShort();
        h.sourcePort = buf.getShort();

        h.payloadLength = buf.getInt();
        h.chunkId = buf.getInt();
        h.chunkLength = buf.getInt();

        h.ttl = buf.get();
        buf.get(h.checksum);

        return h;
    }


    // CHECKSUM =================================================================
    public void computeChecksum(byte[] payload) {
        this.checksum = HashUtil.sha256(payload);
    }


    // DEEP COPY ================================================================
    public PacketHeader copy() {
        PacketHeader h = new PacketHeader();

        h.type = type;
        h.sequenceNumber = sequenceNumber;

        h.destinationIp = destinationIp;
        h.sourceIp = sourceIp;

        h.destinationPort = destinationPort;
        h.sourcePort = sourcePort;

        h.payloadLength = payloadLength;
        h.chunkId = chunkId;
        h.chunkLength = chunkLength;

        h.ttl = ttl;
        h.checksum = Arrays.copyOf(checksum, checksum.length);

        return h;
    }
}