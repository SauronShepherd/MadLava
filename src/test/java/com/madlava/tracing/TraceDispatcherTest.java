package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;

class TraceDispatcherTest {
    @Test void dispatcherIsBoundedAndDrainsOnClose() {
        CountDownLatch received = new CountDownLatch(1);
        TraceDispatcher dispatcher = new TraceDispatcher(1, event -> { try { Thread.sleep(20); } catch (InterruptedException ignored) { } received.countDown(); });
        for (int i=0;i<100;i++) dispatcher.submit(Map.of("i",i));
        dispatcher.close();
        assertTrue(dispatcher.produced() <= 100);
        assertTrue(dispatcher.dropped() > 0);
        assertTrue(dispatcher.produced() > 0);
    }
}
