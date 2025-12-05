package net.p2pchat.network;

import net.p2pchat.model.PacketHeader;
import net.p2pchat.util.HashUtil;

import java.net.DatagramPacket;

public class PacketReceiver {

    public static void handle(DatagramPacket packet) {
        byte[] raw = packet.getData();
        PacketHeader header = PacketHeader.fromBytes(raw);

        System.out.println("Header empfangen:");
        System.out.println(header);

        int headerSize = PacketHeader.HEADER_SIZE;
        byte[] payload = new byte[header.payloadLength];

        System.arraycopy(raw, headerSize, payload, 0, header.payloadLength);

        byte[] calc = HashUtil.sha256(payload);

        boolean valid = java.util.Arrays.equals(calc, header.checksum);

        System.out.println("Checksum valid: " + valid);
    }
}