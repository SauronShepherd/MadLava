package com.madlava.reporting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded latest-value queue. When full, the oldest queued record is evicted. */
public final class BoundedSnapshotQueue {
    private final int capacity;
    private final Deque<String> queue;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final LongAdder dropped = new LongAdder();

    public BoundedSnapshotQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.queue = new ArrayDeque<>(capacity);
    }

    /** Evict the oldest queued value atomically when the bounded queue is full. */
    public void submit(String value) {
        lock.lock();
        try {
            if (queue.size() == capacity) {
                queue.removeFirst();
                dropped.increment();
            }
            queue.addLast(value);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public String poll() {
        lock.lock();
        try {
            return queue.pollFirst();
        } finally {
            lock.unlock();
        }
    }

    /** Wait for a value without periodic wakeups while preserving exact eviction accounting. */
    public String poll(long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                if (remaining <= 0L) {
                    return null;
                }
                remaining = notEmpty.awaitNanos(remaining);
            }
            return queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    public long droppedCount() {
        return dropped.sum();
    }
}
