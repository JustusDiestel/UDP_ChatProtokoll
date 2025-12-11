package net.p2pchat;

import net.p2pchat.network.UdpSocket;
import net.p2pchat.util.SequenceNumberGenerator;

public class NodeContext {

    // Wird in Main gesetzt (NICHT beim Klassenladen)
    public static int localIp;

    // Wird in Main gesetzt
    public static int localPort;

    // Sequenznummern-Generator, global eindeutig
    public static final SequenceNumberGenerator seqGen = new SequenceNumberGenerator();

    // Socket wird in Main initialisiert
    public static UdpSocket socket;
}