package com.madlava.reporting;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class BoundedSnapshotQueueTest {
 @Test void dropsOldestWithoutBlocking(){BoundedSnapshotQueue q=new BoundedSnapshotQueue(2);q.submit("a");q.submit("b");q.submit("c");assertEquals(2,q.size());assertEquals(1,q.droppedCount());assertEquals("b",q.poll());assertEquals("c",q.poll());}
 @Test void rejectsInvalidCapacity(){assertThrows(IllegalArgumentException.class,()->new BoundedSnapshotQueue(0));}
 @Test void concurrentProducersHaveExactDropAccounting() throws Exception {BoundedSnapshotQueue q=new BoundedSnapshotQueue(4);java.util.List<Thread> threads=new java.util.ArrayList<>();for(int i=0;i<1000;i++){final int n=i;Thread t=new Thread(()->q.submit("v"+n));threads.add(t);t.start();}for(Thread t:threads)t.join();assertEquals(4,q.size());assertEquals(996L,q.droppedCount());}
}
