package net.p2pchat.protocol;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PacketFactory {

    public static Packet createAck(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = new PacketHeader();
        h.type = 0x01;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = 0;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createNoAck(int seq, int destIp, int destPort, int[] missingChunkIds) {
        int count = missingChunkIds.length;
        ByteBuffer buf = ByteBuffer.allocate(2 + count * 4);
        buf.putShort((short) count);
        for (int id : missingChunkIds) {
            buf.putInt(id);
        }
        byte[] payload = buf.array();

        PacketHeader h = new PacketHeader();
        h.type = 0x02;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = payload.length;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createHello(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = new PacketHeader();
        h.type = 0x03;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = 0;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createGoodbye(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = new PacketHeader();
        h.type = 0x04;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = 0;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createRoutingUpdate(int seq, int destIp, int destPort, byte[] payload) {
        PacketHeader h = new PacketHeader();
        h.type = 0x08;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = payload.length;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createHeartbeat(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = new PacketHeader();
        h.type = 0x07;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = 0;
        h.ttl = 10;
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createFileChunk(
            int seq,
            int destIp,
            int destPort,
            int chunkId,
            int chunkCount,
            byte[] data
    ) {
        PacketHeader h = new PacketHeader();
        h.type = 0x06;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;              // KORREKT
        h.destinationPort = (short) destPort;  // KORREKT
        h.payloadLength = data.length;
        h.ttl = 10;
        h.chunkId = chunkId;
        h.chunkLength = chunkCount;
        h.computeChecksum(data);

        return new Packet(h, data);
    }

    public static Packet createFileInfo(
            int seq,
            int destIp,
            int destPort,
            int totalChunks,
            String filename
    ) {
        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 + nameBytes.length);

        buf.putInt(totalChunks);
        buf.putShort((short) nameBytes.length);
        buf.put(nameBytes);

        byte[] payload = buf.array();

        PacketHeader h = new PacketHeader();
        h.type = 0x09;
        h.sequenceNumber = seq;
        h.sourceIp = NodeContext.localIp;
        h.sourcePort = (short) NodeContext.localPort;
        h.destinationIp = destIp;
        h.destinationPort = (short) destPort;
        h.payloadLength = payload.length;
        h.ttl = 10;
        h.computeChecksum(payload);

        return new Packet(h, payload);
    }
}