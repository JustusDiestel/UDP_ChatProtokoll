package net.p2pchat.network;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.util.IpUtil;

import java.io.IOException;
import java.net.*;

public class UdpSocket {

    private final int port;
    private DatagramSocket socket;

    private volatile boolean running;

    private Thread receiverThread;
    private Thread retransmissionThread;

    public UdpSocket(int port) {
        this.port = port;
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================
    // RECEIVER THREAD
    // ============================================================
    public void startReceiver() {

        running = true;

        receiverThread = new Thread(() -> {
            byte[] buffer = new byte[4096];

            while (running) {

                try {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dp);

                    PacketReceiver.handle(dp);

                } catch (IOException e) {
                    if (running)
                        System.err.println(e.getMessage());
                }
            }

            System.out.println("[UDP] ReceiverThread beendet.");
        });

        receiverThread.start();
    }


    // ============================================================
    // RETRANSMISSION LOOP
    // ============================================================
    public void startRetransmissionLoop() {

        retransmissionThread = new Thread(() -> {

            while (running) {

                long now = System.currentTimeMillis();

                for (var entry : PendingPackets.getPending().entrySet()) {

                    int key = entry.getKey();
                    var p = entry.getValue();

                    if (now - p.timestamp < 3000)
                        continue;

                    if (p.attempts >= 3) {
                        PendingPackets.clear(key);
                        continue;
                    }

                    try {
                        InetAddress addr = InetAddress.getByName(
                                IpUtil.intToIp(p.destIp)
                        );

                        if (p.isFrame) {

                            if (p.missingChunks != null) {

                                for (int miss : p.missingChunks) {
                                    int idx = miss % p.frameChunks.length;
                                    Packet resend = p.frameChunks[idx];
                                    if (resend != null)
                                        sendPacket(resend, addr, p.destPort);
                                }

                            } else {

                                for (Packet fp : p.frameChunks) {
                                    if (fp != null)
                                        sendPacket(fp, addr, p.destPort);
                                }
                            }

                        } else {

                            sendPacket(p.singlePacket, addr, p.destPort);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    p.attempts++;
                    p.timestamp = now;
                }

                try { Thread.sleep(1000); }
                catch (InterruptedException ignored) {}
            }

            System.out.println("[UDP] RetransmissionThread beendet.");
        });

        retransmissionThread.start();
    }


    // ============================================================
    // SEND (UNRELIABLE)
    // ============================================================
    public void sendPacket(Packet p, InetAddress addr, int port) {

        byte[] data = p.toBytes();

        try {
            DatagramPacket dp = new DatagramPacket(data, data.length, addr, port);
            socket.send(dp);

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }


    // ============================================================
    // SEND (RELIABLE)
    // ============================================================
    public void sendReliable(Packet p, String ip, int port) {

        try {
            InetAddress addr = InetAddress.getByName(ip);

            sendPacket(p, addr, port);

            PendingPackets.trackSingle(
                    p,
                    IpUtil.ipToInt(ip),
                    port
            );

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


    // ============================================================
    // CLEAN SHUTDOWN
    // ============================================================
    public void stop() {

        System.out.println("[UDP] Shutdown…");

        running = false;

        try { socket.close(); } catch (Exception ignored) {}

        try {
            if (receiverThread != null && receiverThread.isAlive())
                receiverThread.join(250);
        } catch (InterruptedException ignored) {}

        try {
            if (retransmissionThread != null && retransmissionThread.isAlive())
                retransmissionThread.join(250);
        } catch (InterruptedException ignored) {}

        System.out.println("[UDP] gestoppt.");
    }
}