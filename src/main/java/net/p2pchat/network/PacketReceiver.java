package net.p2pchat.network;

import net.p2pchat.model.PacketHeader;
import net.p2pchat.util.HashUtil;
import net.p2pchat.util.ReceivedHistory;

import java.net.DatagramPacket;
import java.util.Arrays;

public class PacketReceiver {

    private static final ReceivedHistory receivedHistory = new ReceivedHistory();

    public static void handle(DatagramPacket packet) {
        int len = packet.getLength();

        byte[] raw = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + len);
        PacketHeader header = PacketHeader.fromBytes(raw);

        boolean duplicate = receivedHistory.isDuplicate(header.sourceIp, header.chunkId);

        if (duplicate) {
            System.out.println("Duplikat erkannt: seq=" + header.sequenceNumber +
                    " von sourceIp=" + header.sourceIp + " -> verwerfe.");
            return;
        }
        System.out.println("Neues Paket: type=" + header.type +
                ", seq=" + header.sequenceNumber +
                ", len=" + header.payloadLength);



        int headerSize = PacketHeader.HEADER_SIZE;
        if (raw.length < headerSize + header.payloadLength) {
            System.out.println("Warnung: Paket zu kurz für angegebenes PayloadLength.");
            return;
        }

        byte[] payload = Arrays.copyOfRange(raw, headerSize, headerSize + header.payloadLength);


        byte[] calc = HashUtil.sha256(payload);
        boolean valid = Arrays.equals(calc, header.checksum);
        System.out.println("Checksum valid: " + valid);

        if (!valid) {
            System.out.println("Ungültige Checksumme -> Paket verwerfen.");
            return;
        }
    }
}