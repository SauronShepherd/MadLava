package com.madlava.core;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class FeatureCircuitBreaker {
    private final int threshold; private final long cooldownMillis; private final Clock clock; private final AtomicInteger failures=new AtomicInteger(); private final AtomicLong openedAt=new AtomicLong(-1);
    public FeatureCircuitBreaker(int threshold,long cooldownMillis,Clock clock){if(threshold<1||cooldownMillis<0)throw new IllegalArgumentException("Invalid circuit breaker bounds");this.threshold=threshold;this.cooldownMillis=cooldownMillis;this.clock=clock;}
    public boolean allow(){long opened=openedAt.get();if(opened<0)return true;if(clock.millis()-opened<cooldownMillis)return false;if(openedAt.compareAndSet(opened,-1)){failures.set(0);return true;}return openedAt.get()<0;}
    public void success(){failures.set(0);openedAt.set(-1);}
    public void failure(){if(failures.incrementAndGet()>=threshold)openedAt.compareAndSet(-1,clock.millis());}
    public int failures(){return failures.get();}
    /** Pure status query: unlike allow(), this never transitions or resets the breaker. */
    public boolean open(){long opened=openedAt.get();return opened>=0&&clock.millis()-opened<cooldownMillis;}
}
