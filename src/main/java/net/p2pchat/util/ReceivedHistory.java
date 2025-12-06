package net.p2pchat.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ReceivedHistory {

    private final Map<Integer, Set<Integer>> history = new ConcurrentHashMap<>();

    public boolean isDuplicate(int sourceIp, int seq) {
        Set<Integer> set = history.computeIfAbsent(sourceIp, __ -> new ConcurrentSkipListSet<>());
        boolean exists = set.contains(seq);
        if (!exists) set.add(seq);
        return exists;
    }
}