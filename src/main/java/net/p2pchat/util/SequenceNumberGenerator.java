package net.p2pchat.util;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceNumberGenerator {

    private static final int MAX_SEQ = Integer.MAX_VALUE; // 2^31-1
    private final AtomicInteger seq = new AtomicInteger(1);

    public int next() {
        int current = seq.getAndIncrement();

        // Wenn wir über MAX_SEQ hinauskommen → sauberer Wrap auf 1
        if (current >= MAX_SEQ) {
            seq.compareAndSet(current + 1, 1);
        }

        return current;
    }
}