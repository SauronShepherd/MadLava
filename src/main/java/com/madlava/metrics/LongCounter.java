package com.madlava.metrics;
import java.util.concurrent.atomic.LongAdder;
public final class LongCounter { private final LongAdder value=new LongAdder(); public void increment(){value.increment();} public void add(long delta){value.add(delta);} public long value(){return value.sum();} public long reset(){return value.sumThenReset();} }
