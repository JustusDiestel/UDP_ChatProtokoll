package net.p2pchat.util;

import java.net.*;
import java.util.Enumeration;

public class IpUtil {

    /**
     * Liefert die erste IPv4-Adresse des Systems als 32-bit Integer.
     * Format: a.b.c.d → (a<<24) | (b<<16) | (c<<8) | d
     */
    public static int getLocalIpAsInt() {

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // loopback (127.0.0.1) ignorieren
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    InetAddress inet = addr.getAddress();

                    if (inet instanceof Inet4Address ipv4) {
                        byte[] bytes = ipv4.getAddress();
                        return ((bytes[0] & 0xFF) << 24) |
                                ((bytes[1] & 0xFF) << 16) |
                                ((bytes[2] & 0xFF) << 8) |
                                (bytes[3] & 0xFF);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Ermitteln der lokalen IPv4-Adresse", e);
        }

        // Fallback: 127.0.0.1
        return (127 << 24) | 1;
    }


    /**
     * Wandelt einen 32-bit-Integer zurück in String-IPv4.
     */
    public static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip >> 24) & 0xFF,
                (ip >> 16) & 0xFF,
                (ip >> 8) & 0xFF,
                ip & 0xFF);
    }
}