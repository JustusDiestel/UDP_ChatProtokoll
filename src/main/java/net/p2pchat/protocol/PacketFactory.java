package net.p2pchat.protocol;

import net.p2pchat.model.Packet;
import net.p2pchat.model.PacketHeader;

public class PacketFactory {

    public static Packet createAck(int seq, int srcIp, int destIp){
        PacketHeader header = new PacketHeader();
        header.type = 0x01; // ACK
        header.sequenceNumber = seq;
        header.sourceIp = srcIp;
        header.destinationIp = destIp;
        header.payloadLength = 0;
        header.ttl = 10;
        header.computeChecksum(new byte[0]);

        return new Packet(header, new byte[0]);
    }

    public static Packet createNoAck(int seq, int srcIp, int destIp, int missingChunkId) {
        byte[] payload = new byte[4];
        payload[0] = (byte) ((missingChunkId >> 24) & 0xFF);
        payload[1] = (byte) ((missingChunkId >> 16) & 0xFF);
        payload[2] = (byte) ((missingChunkId >> 8) & 0xFF);
        payload[3] = (byte) (missingChunkId & 0xFF);

        PacketHeader header = new PacketHeader();
        header.type = 0x02; // NO_ACK
        header.sequenceNumber = seq;
        header.sourceIp = srcIp;
        header.destinationIp = destIp;
        header.payloadLength = payload.length;
        header.ttl = 10;
        header.computeChecksum(payload);

        return new Packet(header, payload);
    }


}
