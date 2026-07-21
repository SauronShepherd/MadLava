package com.madlava.reporting;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.LongAdder;

public final class BoundedSnapshotQueue {
    private final ArrayBlockingQueue<String> queue; private final LongAdder dropped = new LongAdder();
    public BoundedSnapshotQueue(int capacity){if(capacity<1)throw new IllegalArgumentException("Capacity must be positive");queue=new ArrayBlockingQueue<>(capacity);}
    public void submit(String value){if(!queue.offer(value)){queue.poll();dropped.increment();queue.offer(value);}}
    public String poll(){return queue.poll();} public int size(){return queue.size();} public long droppedCount(){return dropped.sum();}
}
