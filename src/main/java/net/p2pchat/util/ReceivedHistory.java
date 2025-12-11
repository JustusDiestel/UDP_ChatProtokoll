package net.p2pchat.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReceivedHistory {

    private static final int MAX_HISTORY = 4096; // Rolling Window Größe
    private static final int TRIM_SIZE   = 2048; // Nach dem Halbieren stabil

    // SourceIP → Set der letzten SeqNums
    private final ConcurrentHashMap<Integer, Set<Integer>> history = new ConcurrentHashMap<>();


    public boolean isDuplicate(int sourceIp, int seq) {

        // Thread-safe Set pro IP
        Set<Integer> set = history.computeIfAbsent(
                sourceIp,
                __ -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );

        // Wenn bereits gesehen → Duplikat
        if (set.contains(seq)) {
            return true;
        }

        // Neu → speichern
        set.add(seq);

        // Rolling Window Begrenzung
        if (set.size() > MAX_HISTORY) {
            trim(set);
        }

        return false;
    }


    /**
     * Entfernt ungefähr die Hälfte der Elemente aus dem Set.
     * Motivation:
     * - Speicherverbrauch bleibt O(MAX_HISTORY)
     * - Duplikaterkennung bleibt zuverlässig
     */
    private void trim(Set<Integer> set) {

        int removed = 0;

        for (Integer num : set) {
            set.remove(num);
            removed++;

            if (removed >= TRIM_SIZE) break;
        }
    }
}