package net.p2pchat.util;

import java.net.*;
import java.util.Enumeration;

public class IpUtil {

    /**
     * Liefert die "beste" IPv4-Adresse:
     * - nicht loopback
     * - nicht docker/virtual NIC
     * - bevorzugt physische NICs
     * - ansonsten VPN
     * - ansonsten loopback
     */
    public static int getLocalIpAsInt() {

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

            Inet4Address bestMatch = null;

            while (ifaces.hasMoreElements()) {
                NetworkInterface nic = ifaces.nextElement();

                if (!nic.isUp() || nic.isLoopback())
                    continue;

                // Docker, VirtualBox, VMNet überspringen
                String name = nic.getDisplayName().toLowerCase();
                if (name.contains("docker") ||
                        name.contains("vbox")   ||
                        name.contains("virtual") ||
                        name.contains("vmnet"))
                    continue;

                for (InterfaceAddress ia : nic.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();

                    if (addr instanceof Inet4Address ipv4) {
                        bestMatch = ipv4;
                        break;
                    }
                }
                if (bestMatch != null) break;
            }

            if (bestMatch != null) {
                byte[] b = bestMatch.getAddress();
                return ((b[0] & 0xFF) << 24) |
                        ((b[1] & 0xFF) << 16) |
                        ((b[2] & 0xFF) << 8)  |
                        (b[3] & 0xFF);
            }

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Bestimmen der lokalen IPv4-Adresse", e);
        }

        // Letzte Option: loopback (aber sauber berechnet!)
        return ipToInt("127.0.0.1");
    }


    /**
     * Int → IPv4 String Format a.b.c.d
     */
    public static String intToIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }


    /**
     * IPv4 String → int (mit Validierung)
     */
    public static int ipToInt(String ip) {

        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4)
            throw new IllegalArgumentException("Ungültige IPv4-Adresse: " + ip);

        int result = 0;

        for (int i = 0; i < 4; i++) {
            int val = Integer.parseInt(parts[i]);
            if (val < 0 || val > 255)
                throw new IllegalArgumentException("Ungültiges IPv4-Byte: " + parts[i]);
            result |= (val & 0xFF) << (8 * (3 - i));
        }

        return result;
    }
}