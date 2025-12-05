package net.p2pchat.network;

import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.util.IpUtil;

import java.io.IOException;
import java.net.*;

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


    public void startRetransmissionLoop() {
        Thread t = new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();

                for (var entry : PendingPackets.getPending().entrySet()) {
                    int seq = entry.getKey();
                    var pending = entry.getValue();

                    if (now - pending.timestamp > 3000) {

                        if (pending.attempts >= 3) {
                            System.out.println("Gebe Paket seq=" + seq + " auf.");
                            PendingPackets.clear(seq);
                            continue;
                        }

                        System.out.println("Retransmission seq=" + seq +
                                " (Versuch " + pending.attempts + ")");

                        try {
                            this.sendPacket(pending.packet, InetAddress.getByName(pending.ip), pending.port);
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }

                        pending.attempts++;
                        pending.timestamp = now;
                    }
                }

                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        });

        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        socket.close();
    }

    // In UdpSocket.java
    public void sendPacket(Packet p, InetAddress addr, int port) {
        byte[] data = p.toBytes();

        try {
            DatagramPacket dp = new DatagramPacket(data, data.length, addr, port);
            socket.send(dp);
        } catch (Exception e) {
            System.err.println("Sendefehler: " + e.getMessage());
        }
    }

    public void sendReliable(Packet p, String ip, int port) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            sendPacket(p, addr, port);
            PendingPackets.track(p, ip, port);   // <- wichtig
        } catch (Exception e) {
            System.err.println("Sendefehler: " + e.getMessage());
        }
    }

}