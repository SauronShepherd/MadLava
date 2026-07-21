package com.madlava.reporting;

import com.madlava.runtime.RuntimeContext;
import com.madlava.features.JvmMetricsCollector;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SnapshotScheduler implements AutoCloseable {
    private final RuntimeContext context; private final BoundedSnapshotQueue queue; private final String version,hash; private final ScheduledExecutorService executor; private final AtomicLong sequence=new AtomicLong(); private final AtomicBoolean building=new AtomicBoolean(); private final JvmMetricsCollector collector=new JvmMetricsCollector();
    public SnapshotScheduler(RuntimeContext context,BoundedSnapshotQueue queue,String version,String hash){this.context=context;this.queue=queue;this.version=version;this.hash=hash;this.executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"madlava-snapshot");t.setDaemon(true);return t;});}
    public void start(long intervalSeconds){executor.scheduleWithFixedDelay(()->emit(false),0,intervalSeconds,TimeUnit.SECONDS);} public void emit(boolean finalSnapshot){if(!building.compareAndSet(false,true))return;try{Snapshot s=new Snapshot(version,hash,sequence.incrementAndGet(),queue.droppedCount(),Instant.now(context.clock()),finalSnapshot,context.featureRegistry().snapshot(),collector.collect());queue.submit(JsonEncoder.encode(s));}finally{building.set(false);}}
    @Override public void close(){executor.shutdownNow();emit(true);}
}
