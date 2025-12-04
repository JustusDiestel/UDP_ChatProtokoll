package net.p2pchat.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UdpSocket {

    private final int port;
    private DatagramSocket socket;
    private boolean running;

    public UdpSocket(int port) {
        this.port = port;

        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw new RuntimeException("Konnte UDP-Socket nicht starten", e);
        }
    }

    public void startReceiver() {
        running = true;

        Thread receiverThread = new Thread(() -> {
            byte[] buffer = new byte[2048];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Übergabe an PacketReceiver
                    PacketReceiver.handle(packet);

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Fehler beim Empfang: " + e.getMessage());
                    }
                }
            }
        });

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void stop() {
        running = false;
        socket.close();
    }
}