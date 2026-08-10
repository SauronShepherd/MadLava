package com.madlava.serialization;

import com.madlava.io.RuntimeObservationBridge;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SerializationObservationTest {
    @Test void measurementFailureBeforeActionIsFailOpenAndDoesNotLeakDepth() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        String result=SerializationObservation.observe("java","position","ESTIMATED",()->{throw new IllegalStateException("probe");},()->{calls.incrementAndGet();return "ok";});
        assertEquals("ok",result);
        assertEquals(1,calls.get());
        assertTrue(RuntimeObservationBridge.serializationEnter(),"serialization depth leaked after failed measurement");
        RuntimeObservationBridge.serializationExit(true,"verify",-1,true,"none","UNAVAILABLE");
    }

    @Test void measurementFailureAfterSuccessfulActionDoesNotReplaceResult() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        String result=SerializationObservation.observe("java","position","ESTIMATED",()->{
            if(reads.getAndIncrement()==0)return 10L;
            throw new AssertionError("post-measurement failed");
        },()->"application-result");
        assertEquals("application-result",result);
    }

    @Test void applicationErrorIsRethrownAndDepthIsRestored(){
        AssertionError failure=assertThrows(AssertionError.class,()->SerializationObservation.observe("java","position","EXACT",()->0L,()->{throw new AssertionError("boom");}));
        assertEquals("boom",failure.getMessage());
        assertTrue(RuntimeObservationBridge.serializationEnter(),"serialization depth leaked after application Error");
        RuntimeObservationBridge.serializationExit(true,"verify",-1,true,"none","UNAVAILABLE");
    }
}
