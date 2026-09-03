package com.madlava.core;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureCircuitBreakerTest {
    @Test void opensAndRecoversAfterCooldown(){
        MutableClock clock=new MutableClock();FeatureCircuitBreaker breaker=new FeatureCircuitBreaker(2,100,clock);
        breaker.failure();assertTrue(breaker.allow());breaker.failure();assertFalse(breaker.allow());clock.millis=101;
        assertTrue(breaker.allow());assertEquals(0,breaker.failures());
    }

    @Test void statusQueryDoesNotResetStateAfterCooldown(){
        MutableClock clock=new MutableClock();FeatureCircuitBreaker breaker=new FeatureCircuitBreaker(1,100,clock);
        breaker.failure();assertTrue(breaker.open());clock.millis=101;
        assertFalse(breaker.open());assertEquals(1,breaker.failures(),"open() must be a pure query");
        assertTrue(breaker.allow());assertEquals(0,breaker.failures());
    }

    static final class MutableClock extends Clock{
        long millis;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(millis);}
    }
}
