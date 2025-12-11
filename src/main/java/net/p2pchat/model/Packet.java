package net.p2pchat.model;

public class Packet {

    public PacketHeader header;
    public byte[] payload;

    public Packet(PacketHeader header, byte[] payload) {
        this.header = header;
        this.payload = (payload == null ? new byte[0] : payload);
    }

    // Deep Copy – korrekt mit Header.copy()
    public Packet copy() {
        PacketHeader newHeader = header.copy();
        byte[] newPayload = new byte[payload.length];
        System.arraycopy(payload, 0, newPayload, 0, payload.length);
        return new Packet(newHeader, newPayload);
    }

    // Serialisierung: Header + Payload
    public byte[] toBytes() {
        byte[] h = header.toBytes();
        byte[] result = new byte[h.length + payload.length];

        System.arraycopy(h, 0, result, 0, h.length);
        System.arraycopy(payload, 0, result, h.length, payload.length);

        return result;
    }
}