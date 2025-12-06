package net.p2pchat.model;

import net.p2pchat.util.HashUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PacketHeader {

    public byte type;                  // 1
    public int sequenceNumber;         // 4
    public int destinationIp;          // 4
    public int sourceIp;               // 4

    public short sourcePort;           // 2  (NEU)
    public short destinationPort;      // 2  (NEU)

    public int payloadLength;          // 4
    public int chunkId;                // 4
    public int chunkLength;            // 4

    public byte ttl;                   // 1

    public byte[] checksum = new byte[32]; // 32 bytes SHA-256

    public static final int HEADER_SIZE = 62; // korrekt berechnet

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);

        buffer.put(type);
        buffer.putInt(sequenceNumber);
        buffer.putInt(destinationIp);
        buffer.putInt(sourceIp);

        buffer.putShort(sourcePort);
        buffer.putShort(destinationPort);

        buffer.putInt(payloadLength);
        buffer.putInt(chunkId);
        buffer.putInt(chunkLength);

        buffer.put(ttl);
        buffer.put(checksum);

        return buffer.array();
    }

    public static PacketHeader fromBytes(byte[] bytes) {
        if (bytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Header zu klein! " + bytes.length + " < " + HEADER_SIZE);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        PacketHeader h = new PacketHeader();

        h.type = buffer.get();
        h.sequenceNumber = buffer.getInt();
        h.destinationIp = buffer.getInt();
        h.sourceIp = buffer.getInt();

        h.sourcePort = buffer.getShort();
        h.destinationPort = buffer.getShort();

        h.payloadLength = buffer.getInt();
        h.chunkId = buffer.getInt();
        h.chunkLength = buffer.getInt();

        h.ttl = buffer.get();

        buffer.get(h.checksum);

        return h;
    }

    public void computeChecksum(byte[] payload) {
        this.checksum = HashUtil.sha256(payload);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("PacketHeader {\n");
        sb.append("  type            = ").append(type).append("\n");
        sb.append("  sequenceNumber  = ").append(sequenceNumber).append("\n");
        sb.append("  destinationIp   = ").append(destinationIp).append("\n");
        sb.append("  sourceIp        = ").append(sourceIp).append("\n");
        sb.append("  sourcePort      = ").append(sourcePort & 0xFFFF).append("\n");
        sb.append("  destinationPort = ").append(destinationPort & 0xFFFF).append("\n");
        sb.append("  payloadLength   = ").append(payloadLength).append("\n");
        sb.append("  chunkId         = ").append(chunkId).append("\n");
        sb.append("  chunkLength     = ").append(chunkLength).append("\n");
        sb.append("  ttl             = ").append(ttl).append("\n");
        sb.append("  checksum        = ");

        for (byte b : checksum) {
            sb.append(String.format("%02X", b)).append(" ");
        }
        sb.append("\n}");

        return sb.toString();
    }

}