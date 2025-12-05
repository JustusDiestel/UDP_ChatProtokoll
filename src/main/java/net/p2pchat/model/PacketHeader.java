package net.p2pchat.model;

import net.p2pchat.util.HashUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PacketHeader {

    public byte type;                      // 1 Byte
    public int sequenceNumber;             // 4
    public int destinationIp;              // 4
    public int sourceIp;                   // 4
    public int payloadLength;              // 4
    public int chunkId;                    // 4
    public int chunkLength;                // 4
    public byte ttl;                       // 1
    public byte[] checksum = new byte[32]; // 32 Bytes SHA-256

    public static final int HEADER_SIZE = 58;

    public byte[] toBytes(){
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);

        buffer.put(type);
        buffer.putInt(sequenceNumber);
        buffer.putInt(destinationIp);
        buffer.putInt(sourceIp);
        buffer.putInt(payloadLength);
        buffer.putInt(chunkId);
        buffer.putInt(chunkLength);
        buffer.put(ttl);
        buffer.put(checksum);

        return buffer.array();
    }

    public static PacketHeader fromBytes(byte[] bytes){
        if(bytes.length <HEADER_SIZE){
            throw new IllegalArgumentException("Header zu klein!");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        PacketHeader h = new PacketHeader();

        h.type = buffer.get();
        h.sequenceNumber = buffer.getInt();
        h.destinationIp = buffer.getInt();
        h.sourceIp = buffer.getInt();
        h.payloadLength = buffer.getInt();
        h.chunkId = buffer.getInt();
        h.chunkLength = buffer.getInt();
        h.ttl = buffer.get();

        buffer.get(h.checksum);

        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("+-------------------------+\n");
        sb.append("|       PacketHeader      |\n");
        sb.append("+------------+------------+\n");
        sb.append(String.format("| type       | %10d |\n", type));
        sb.append(String.format("| seqNumber  | %10d |\n", sequenceNumber));
        sb.append(String.format("| destIP     | %10d |\n", destinationIp));
        sb.append(String.format("| srcIP      | %10d |\n", sourceIp));
        sb.append(String.format("| payloadLen | %10d |\n", payloadLength));
        sb.append(String.format("| chunkId    | %10d |\n", chunkId));
        sb.append(String.format("| chunkLen   | %10d |\n", chunkLength));
        sb.append(String.format("| ttl        | %10d |\n", ttl));
        sb.append("+------------+------------+\n");
        sb.append("| checksum (32 bytes)    |\n");
        sb.append("+-------------------------+\n");
        sb.append("| ");
        for (byte b : checksum) {
            sb.append(String.format("%02X ", b));
        }
        sb.append("|\n");
        sb.append("+-------------------------+");
        return sb.toString();
    }

    public void computeChecksum(byte[] payload) {
        this.checksum = HashUtil.sha256(payload);
    }

}
