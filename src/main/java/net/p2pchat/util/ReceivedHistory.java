package net.p2pchat.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReceivedHistory {

    private static final int MAX_HISTORY = 4096;
    private static final int TRIM_SIZE   = 2048;

    // Key = "sourceIp:sourcePort"
    private final ConcurrentHashMap<String, Set<Integer>> history =
            new ConcurrentHashMap<>();

    private static String key(int ip, int port) {
        return ip + ":" + port;
    }

    public boolean isDuplicate(int sourceIp, int sourcePort, int seq) {

        String k = key(sourceIp, sourcePort);

        Set<Integer> set = history.computeIfAbsent(
                k,
                __ -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );

        // Bereits gesehen → Duplikat
        if (!set.add(seq)) {
            return true;
        }

        // Rolling Window begrenzen
        if (set.size() > MAX_HISTORY) {
            trim(set);
        }

        return false;
    }

    private void trim(Set<Integer> set) {

        Iterator<Integer> it = set.iterator();
        int removed = 0;

        while (it.hasNext() && removed < TRIM_SIZE) {
            it.next();
            it.remove();
            removed++;
        }
    }
}