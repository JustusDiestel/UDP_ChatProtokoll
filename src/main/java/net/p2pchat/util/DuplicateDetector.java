package net.p2pchat.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateDetector {

    private static final int MAX_HISTORY = 10000;

    private final ConcurrentHashMap<Integer, Set<Integer>> history =
            new ConcurrentHashMap<>();

    public boolean isDuplicate(int sourceIp, int sequenceNumber) {

        Set<Integer> set = history.computeIfAbsent(
                sourceIp,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );

        if (!set.add(sequenceNumber)) {
            return true;
        }

        if (set.size() > MAX_HISTORY) {
            set.clear();
        }

        return false;
    }
}