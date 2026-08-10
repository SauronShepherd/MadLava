package com.madlava.tracing;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TraceDispatcherTest {
    @Test void dispatcherIsBoundedAndDrainsAcceptedEventsOnClose() {
        AtomicInteger delivered=new AtomicInteger();
        TraceDispatcher dispatcher=new TraceDispatcher(16,event->{try{Thread.sleep(1);}catch(InterruptedException ignored){}delivered.incrementAndGet();});
        int accepted=0;for(int i=0;i<100;i++)if(dispatcher.submit(Map.of("i",i)))accepted++;
        dispatcher.close();
        assertEquals(accepted,dispatcher.produced());assertEquals(accepted,delivered.get());assertEquals(100L-accepted,dispatcher.dropped());
    }
    @Test void concurrentCloseNeverAcceptsAnUndeliverableEvent() throws Exception {
        for(int round=0;round<50;round++){
            AtomicInteger delivered=new AtomicInteger();
            TraceDispatcher dispatcher=new TraceDispatcher(1024,event->delivered.incrementAndGet());
            Thread producer=new Thread(()->{for(int i=0;i<1000;i++)dispatcher.submit(Map.of("i",i));});
            producer.start();
            dispatcher.close();
            producer.join();
            assertEquals(dispatcher.produced(),(long)delivered.get());
        }
    }
    @Test void sinkFailuresAreCountedWithoutKillingTheDispatcher() {
        AtomicInteger attempts=new AtomicInteger();
        TraceDispatcher dispatcher=new TraceDispatcher(8,event->{attempts.incrementAndGet();throw new IllegalStateException("sink down");});
        assertTrue(dispatcher.submit(Map.of("i",1)));
        assertTrue(dispatcher.submit(Map.of("i",2)));
        dispatcher.close();
        assertEquals(2,attempts.get());
        assertEquals(2L,dispatcher.produced());
        assertEquals(2L,dispatcher.sinkFailures());
    }

}
