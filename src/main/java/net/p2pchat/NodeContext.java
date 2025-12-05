package net.p2pchat;

import net.p2pchat.network.UdpSocket;
import net.p2pchat.util.IpUtil;
import net.p2pchat.util.SequenceNumberGenerator;

import java.net.InetAddress;

public class NodeContext {

    public static final int localIp = IpUtil.getLocalIpAsInt();
    public static final SequenceNumberGenerator seqGen = new SequenceNumberGenerator();
    public static UdpSocket socket; // später gesetzt

}