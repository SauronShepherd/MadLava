package com.madlava.tracing;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/** Bounded, fail-open trace event handoff. */
public final class TraceDispatcher implements AutoCloseable {
    private final ArrayBlockingQueue<Map<String,Object>> queue; private final Consumer<Map<String,Object>> sink;
    private final LongAdder produced=new LongAdder(), dropped=new LongAdder(); private final AtomicBooleanLike closed=new AtomicBooleanLike(); private final Thread worker;
    public TraceDispatcher(int capacity, Consumer<Map<String,Object>> sink){if(capacity<1||sink==null)throw new IllegalArgumentException();this.queue=new ArrayBlockingQueue<>(capacity);this.sink=sink;worker=new Thread(this::run,"madlava-trace-dispatcher");worker.setDaemon(true);worker.start();}
    public boolean submit(Map<String,Object> event){if(closed.get()||event==null)return false;if(!queue.offer(event)){dropped.increment();return false;}produced.increment();return true;}
    public long produced(){return produced.sum();} public long dropped(){return dropped.sum();}
    private void run(){while(!closed.get()||!queue.isEmpty()){try{Map<String,Object> event=queue.poll(100,TimeUnit.MILLISECONDS);if(event!=null)try{sink.accept(event);}catch(Throwable ignored){}}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
    public void close(){closed.set(true);worker.interrupt();try{worker.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static final class AtomicBooleanLike { private volatile boolean value; boolean get(){return value;} void set(boolean v){value=v;} }
}
