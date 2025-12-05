package net.p2pchat.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class ReceivedHistory {
    private final Map<Integer, Set<Integer>> receivedHistory = new ConcurrentHashMap<>();
    DuplicateDetector detector = new DuplicateDetector();

    public boolean isDuplicate(int sourceIp, int sequenceNumber){
        return detector.isDuplicateGet(sourceIp, sequenceNumber);
    }
}
