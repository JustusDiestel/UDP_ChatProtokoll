package net.p2pchat.util;

import java.security.MessageDigest;

public class HashUtil {

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 Fehler", e);
        }
    }
}