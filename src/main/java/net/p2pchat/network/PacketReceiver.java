package net.p2pchat.network;

import java.net.DatagramPacket;

public class PacketReceiver {

    public static void handle(DatagramPacket packet) {
        int len = packet.getLength();
        byte[] data = packet.getData();
        int port = packet.getPort();
        String addr = packet.getAddress().getHostAddress();

        System.out.println("Empfangen von " + addr + ":" + port + " | " + len + " Bytes");

        // Später: hier Protokoll-Parsing (Header + Payload)
    }
}