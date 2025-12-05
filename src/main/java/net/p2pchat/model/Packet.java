package net.p2pchat.model;

public class Packet {
    public PacketHeader header;
    public byte[] payload;


    public Packet(PacketHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload;
    }

    public byte[] toBytes(){
        byte[] headerByte = header.toBytes();
        byte[] newByte = new byte[headerByte.length + payload.length];

        System.arraycopy(headerByte, 0, newByte, 0, headerByte.length);
        System.arraycopy(payload, 0, newByte, headerByte.length, payload.length);

        return newByte;
    }


}
