package net.p2pchat.util;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceNumberGenerator {

    private static final int MAX_SEQ = Integer.MAX_VALUE;
    private final AtomicInteger seq = new AtomicInteger(0);

    public int next() {
        return seq.updateAndGet(v -> (v >= MAX_SEQ) ? 1 : v + 1);
    }
}