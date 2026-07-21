package com.madlava.reporting;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class BoundedSnapshotQueueTest { @Test void dropsOldestWithoutBlocking(){BoundedSnapshotQueue q=new BoundedSnapshotQueue(2);q.submit("a");q.submit("b");q.submit("c");assertEquals(2,q.size());assertEquals(1,q.droppedCount());assertEquals("b",q.poll());assertEquals("c",q.poll());} @Test void rejectsInvalidCapacity(){assertThrows(IllegalArgumentException.class,()->new BoundedSnapshotQueue(0));}}
