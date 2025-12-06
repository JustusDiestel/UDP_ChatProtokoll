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
            throw new RuntimeException(e);
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
                    PacketReceiver.handle(packet);
                } catch (IOException e) {
                    if (running) System.err.println(e.getMessage());
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
                            PendingPackets.clear(seq);
                            continue;
                        }

                        try {
                            InetAddress addr = InetAddress.getByName(IpUtil.intToIp(pending.destIp));
                            sendPacket(pending.packet, addr, pending.destPort);
                        } catch (Exception e) {
                            e.printStackTrace();
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

    public void sendPacket(Packet p, InetAddress addr, int port) {
        byte[] data = p.toBytes();
        try {
            DatagramPacket dp = new DatagramPacket(data, data.length, addr, port);
            socket.send(dp);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void sendReliable(Packet p, String ip, int port) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            sendPacket(p, addr, port);
            PendingPackets.track(p, IpUtil.ipToInt(ip), port);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public InetAddress socketAddressForIp(int ip) {
        try {
            return InetAddress.getByName(IpUtil.intToIp(ip));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}