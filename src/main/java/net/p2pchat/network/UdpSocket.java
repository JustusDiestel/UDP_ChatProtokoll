package net.p2pchat.network;

import net.p2pchat.NodeContext;
import net.p2pchat.model.Packet;
import net.p2pchat.protocol.PendingPackets;
import net.p2pchat.routing.Route;
import net.p2pchat.routing.RoutingTable;
import net.p2pchat.util.IpUtil;

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

    public void startReceiver() {
        running = true;

        receiverThread = new Thread(() -> {
            byte[] buffer = new byte[4096];

            while (running) {
                try {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dp);
                    PacketReceiver.handle(dp);
                } catch (Exception e) {
                    if (running) System.err.println(e.getMessage());
                }
            }
        });

        receiverThread.start();
    }

    public void startRetransmissionLoop() {

        retransmissionThread = new Thread(() -> {

            while (running) {

                long now = System.currentTimeMillis();

                for (var entry : PendingPackets.getPending().entrySet()) {

                    long key = entry.getKey();
                    PendingPackets.Pending p = entry.getValue();

                    if (now - p.timestamp < 3000) continue;

                    if (p.attempts >= 3) {
                        if (p.isFrame)
                            PendingPackets.clearFrame(p.sequenceNumber, p.frameIndex);
                        else
                            PendingPackets.clearSingle(p.sequenceNumber);
                        continue;
                    }

                    Route r = RoutingTable.getRoute(p.destIp, p.destPort);
                    if (r == null) {
                        p.attempts++;
                        p.timestamp = now;
                        continue;
                    }

                    InetAddress nextHop;
                    try {
                        nextHop = InetAddress.getByName(IpUtil.intToIp(r.nextHopIp));
                    } catch (Exception e) {
                        p.attempts++;
                        p.timestamp = now;
                        continue;
                    }

                    try {
                        if (p.isFrame) {

                            if (p.missingChunks != null) {
                                for (int miss : p.missingChunks) {
                                    for (Packet fp : p.frameChunks) {
                                        if (fp != null && fp.header.chunkId == miss) {
                                            sendPacket(fp, nextHop, r.nextHopPort);
                                            break;
                                        }
                                    }
                                }
                            } else {
                                for (Packet fp : p.frameChunks) {
                                    if (fp != null)
                                        sendPacket(fp, nextHop, r.nextHopPort);
                                }
                            }

                        } else {
                            sendPacket(p.singlePacket, nextHop, r.nextHopPort);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    p.attempts++;
                    p.timestamp = now;
                }

                try { Thread.sleep(1000); }
                catch (InterruptedException ignored) {}
            }
        });

        retransmissionThread.start();
    }

    public void sendPacket(Packet p, InetAddress addr, int port) {
        try {
            byte[] data = p.toBytes();
            socket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void sendReliable(Packet p, String nextHopIp, int nextHopPort) {
        try {
            InetAddress addr = InetAddress.getByName(nextHopIp);
            sendPacket(p, addr, nextHopPort);

            // Single-Packet zuverlässig
            PendingPackets.trackSingle(
                    p,
                    p.header.destinationIp,
                    p.header.destinationPort & 0xFFFF
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

    public void stop() {
        running = false;
        try { socket.close(); } catch (Exception ignored) {}

        try {
            if (receiverThread != null) receiverThread.join(250);
            if (retransmissionThread != null) retransmissionThread.join(250);
        } catch (InterruptedException ignored) {}
    }
}