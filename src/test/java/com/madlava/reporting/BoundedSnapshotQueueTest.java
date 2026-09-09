package com.madlava.reporting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BoundedSnapshotQueueTest {
    @Test void dropsOldestWithoutBlocking(){BoundedSnapshotQueue q=new BoundedSnapshotQueue(2);q.submit("a");q.submit("b");q.submit("c");assertEquals(2,q.size());assertEquals(1,q.droppedCount());assertEquals("b",q.poll());assertEquals("c",q.poll());}
    @Test void rejectsInvalidCapacity(){assertThrows(IllegalArgumentException.class,()->new BoundedSnapshotQueue(0));}
    @Test void concurrentProducersHaveExactDropAccounting() throws Exception {BoundedSnapshotQueue q=new BoundedSnapshotQueue(4);java.util.List<Thread> threads=new java.util.ArrayList<>();for(int i=0;i<1000;i++){final int n=i;Thread t=new Thread(()->q.submit("v"+n));threads.add(t);t.start();}for(Thread t:threads)t.join();assertEquals(4,q.size());assertEquals(996L,q.droppedCount());}

    @Test void blockingPollSleepsUntilProducerSignals() throws Exception {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue(2);
        AtomicReference<String> result = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try {
                result.set(queue.poll(5, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();
        Thread.sleep(50);
        assertTrue(consumer.isAlive());

        queue.submit("wake");
        consumer.join(1000);

        assertFalse(consumer.isAlive());
        assertEquals("wake", result.get());
        assertEquals(0L, queue.droppedCount());
    }

    @Test void blockingPollIsInterruptibleForWriterLifecycle() throws Exception {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue(1);
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread consumer = new Thread(() -> {
            try {
                queue.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException expected) {
                interrupted.set(true);
            }
        });
        consumer.start();
        Thread.sleep(50);
        consumer.interrupt();
        consumer.join(1000);

        assertFalse(consumer.isAlive());
        assertTrue(interrupted.get());
    }
}
