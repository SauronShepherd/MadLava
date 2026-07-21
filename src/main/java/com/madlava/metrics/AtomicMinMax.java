package com.madlava.metrics;
import java.util.concurrent.atomic.AtomicLong;
public final class AtomicMinMax { private final AtomicLong min=new AtomicLong(Long.MAX_VALUE),max=new AtomicLong(Long.MIN_VALUE); public void record(long value){min.accumulateAndGet(value,Math::min);max.accumulateAndGet(value,Math::max);} public long minimum(){return min.get();} public long maximum(){return max.get();} public boolean empty(){return min.get()==Long.MAX_VALUE;} }
