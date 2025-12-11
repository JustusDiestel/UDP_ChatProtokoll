package net.p2pchat.protocol;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;

import java.nio.charset.StandardCharsets;

public class PacketFactory {

    private static PacketHeader base(byte type, int seq, int destIp, int destPort, int payloadLen) {

        PacketHeader h = new PacketHeader();

        h.type = type;
        h.sequenceNumber = seq;

        h.destinationIp   = destIp;
        h.sourceIp        = NodeContext.localIp;

        h.destinationPort = (short) destPort;
        h.sourcePort      = (short) NodeContext.localPort;

        h.payloadLength = payloadLen;

        h.chunkId = 0;
        h.chunkLength = 0;

        h.ttl = 64;

        return h;
    }

    public static Packet createAck(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x01, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createNoAck(int frameSeq, int destIp, int destPort, int[] missing) {
        byte[] payload = new byte[missing.length * 4 + 6];
        var buf = java.nio.ByteBuffer.wrap(payload);

        buf.putInt(frameSeq);
        buf.putShort((short)missing.length);
        for (int m : missing) buf.putInt(m);

        PacketHeader h = base((byte)0x02, frameSeq, destIp, destPort, payload.length);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createHello(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x03, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createGoodbye(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x04, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createMessage(int seq, int destIp, int destPort, String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        PacketHeader h = base((byte)0x05, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createFileChunk(int seq, int destIp, int destPort,
                                         int chunkId, int totalChunks, byte[] data) {

        PacketHeader h = base((byte)0x06, seq, destIp, destPort, data.length);
        h.chunkId = chunkId;
        h.chunkLength = totalChunks;
        h.computeChecksum(data);
        return new Packet(h, data);
    }

    // SPEZIFIKATION: payload = NUR filename
    public static Packet createFileInfo(int seq, int destIp, int destPort, String filename) {

        byte[] payload = filename.getBytes(StandardCharsets.UTF_8);

        PacketHeader h = base((byte)0x07, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);

        return new Packet(h, payload);
    }

    public static Packet createHeartbeat(int seq, int destIp, int destPort) {
        byte[] payload = new byte[0];
        PacketHeader h = base((byte)0x08, seq, destIp, destPort, 0);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }

    public static Packet createRoutingUpdate(int seq, int destIp, int destPort, byte[] payload) {
        PacketHeader h = base((byte)0x09, seq, destIp, destPort, payload.length);
        h.computeChecksum(payload);
        return new Packet(h, payload);
    }
}