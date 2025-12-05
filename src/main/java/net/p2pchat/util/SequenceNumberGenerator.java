package net.p2pchat.util;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceNumberGenerator {
    private final AtomicInteger sequenceNumber = new AtomicInteger(1);

    public int next(){
        int value = sequenceNumber.getAndIncrement();
        if (value == 0){
            sequenceNumber.set(1);
            value = sequenceNumber.getAndIncrement();
        }
        return value;
    }
}
