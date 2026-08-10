package com.madlava.reporting;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.LongAdder;

/** Bounded latest-value queue. When full, the oldest queued record is evicted. */
public final class BoundedSnapshotQueue {
    private final ArrayBlockingQueue<String> queue; private final LongAdder dropped = new LongAdder();
    public BoundedSnapshotQueue(int capacity){if(capacity<1)throw new IllegalArgumentException("Capacity must be positive");queue=new ArrayBlockingQueue<>(capacity);}

    /**
     * Producer-side eviction is synchronized so concurrent producers cannot both observe a full
     * queue, over-evict records, or miscount a consumer removal as a producer drop.
     */
    public synchronized void submit(String value){
        if(queue.offer(value))return;
        String evicted=queue.poll();
        if(evicted!=null)dropped.increment();
        // No other producer can refill while this method is synchronized; the consumer can only
        // create more capacity, so this offer must succeed for a non-null value.
        if(!queue.offer(value))throw new IllegalStateException("Unable to enqueue after bounded eviction");
    }
    public String poll(){return queue.poll();} public int size(){return queue.size();} public long droppedCount(){return dropped.sum();}
}
