package org.example.walletservice.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake ID generator.
 * Layout: 1 bit unused | 41 bits timestamp | 10 bits machine id | 12 bits sequence
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z
    private static final long MACHINE_ID_BITS = 10;
    private static final long SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final AtomicLong sequence = new AtomicLong(0L);

    public SnowflakeIdGenerator() {
        this.machineId = 1L; // single instance for local dev
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        long last = lastTimestamp.get();

        if (now == last) {
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) {
                while (now <= last) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence.set(0);
        }

        lastTimestamp.set(now);
        return ((now - EPOCH) << TIMESTAMP_SHIFT) | (machineId << MACHINE_ID_SHIFT) | sequence.get();
    }
}
