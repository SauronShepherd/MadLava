package com.madlava.reporting;

import com.madlava.runtime.RuntimeContext;
import com.madlava.features.JvmMetricsCollector;
import com.madlava.probes.ProbeBridge;
import com.madlava.io.RuntimeObservationBridge;
import com.madlava.pools.ObservedExecutorService;
import com.madlava.diagnostics.DiagnosticsRuntime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SnapshotScheduler implements AutoCloseable {
    private final RuntimeContext context; private final BoundedSnapshotQueue queue; private final String version,hash; private final ScheduledExecutorService executor; private final AtomicLong sequence=new AtomicLong(); private final AtomicBoolean building=new AtomicBoolean(); private final JvmMetricsCollector collector=new JvmMetricsCollector();
    public SnapshotScheduler(RuntimeContext context,BoundedSnapshotQueue queue,String version,String hash){this.context=context;this.queue=queue;this.version=version;this.hash=hash;this.executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"madlava-snapshot");t.setDaemon(true);return t;});}
    public void start(long intervalSeconds){executor.scheduleWithFixedDelay(()->emit(false),0,intervalSeconds,TimeUnit.SECONDS);} public void emit(boolean finalSnapshot){if(!building.compareAndSet(false,true))return;try{Map<String,Object> data=new LinkedHashMap<>(collector.collect());if(context.featureRegistry().snapshot().containsKey("instanceCounting")){ProbeBridge.Snapshot probes=ProbeBridge.snapshot();data.put("instanceCounting",Map.of("successfulOutermostConstructors",probes.constructed(),"accuracy","SELECTED_CLASSES"));Map<String,Object> throwableData=new LinkedHashMap<>();throwableData.put("created",probes.throwableCreated());throwableData.put("explicitThrows",probes.explicitThrows());throwableData.put("propagations",probes.propagations());throwableData.put("jfrThrows",probes.jfrThrows());throwableData.put("jfrState",probes.jfrState());throwableData.put("messageCapture",false);throwableData.put("accuracy","SELECTED_CLASSES");data.put("throwables",throwableData);}if(context.featureRegistry().snapshot().containsKey("streamIo")){RuntimeObservationBridge.Snapshot observation=RuntimeObservationBridge.snapshot();data.put("streamIo",Map.of("observedLayers",observation.ioReport(),"physicalAggregation",false));data.put("networkIo",Map.of("observedLayers",observation.ioReport(),"endpointAnonymized",true));data.put("serialization",Map.of("rootOperations",observation.serializationReport(),"byteAccuracy","SOURCE_SPECIFIC","payloadCapture",false));data.put("threadPools",ObservedExecutorService.snapshot().report());}if(context.featureRegistry().snapshot().containsKey("executionSampling"))data.putAll(DiagnosticsRuntime.snapshot());Snapshot s=new Snapshot(version,hash,sequence.incrementAndGet(),queue.droppedCount(),Instant.now(context.clock()),finalSnapshot,context.featureRegistry().snapshot(),data);queue.submit(JsonEncoder.encode(s));}finally{building.set(false);}}
    @Override public void close(){executor.shutdownNow();emit(true);}
}
