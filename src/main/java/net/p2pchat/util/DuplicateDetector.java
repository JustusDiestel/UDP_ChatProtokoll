package net.p2pchat.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateDetector {

    private static final int MAX_HISTORY = 10000;

    // IP -> Set der letzten SequenceNumbers
    private final ConcurrentHashMap<Integer, Set<Integer>> history = new ConcurrentHashMap<>();

    public boolean isDuplicateGet(int sourceIp, int sequenceNumber) {

        Set<Integer> set = history.computeIfAbsent(
                sourceIp,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );

        // ist diese Seq schon bekannt?
        if (!set.add(sequenceNumber)) {
            return true; // Duplikat!
        }

        // Set zu groß? -> alten Kram entfernen
        if (set.size() > MAX_HISTORY) {
            set.clear(); // einfache Variante: komplett zurücksetzen
        }

        return false; // nicht gesehen -> verarbeiten!
    }
}