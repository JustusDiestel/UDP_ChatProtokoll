package net.p2pchat;

import net.p2pchat.network.UdpSocket;

public class Main {
    public static void main(String[] args) {
        int port = 5000;

        System.out.println("Starte P2P-Chat-Knoten auf Port " + port);

        UdpSocket socket = new UdpSocket(port);
        socket.startReceiver();

        System.out.println("Knoten läuft. Wartet auf UDP-Pakete...");
    }
}