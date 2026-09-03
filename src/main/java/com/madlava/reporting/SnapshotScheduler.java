package com.madlava.reporting;

import com.madlava.runtime.RuntimeContext;
import com.madlava.features.JvmMetricsCollector;
import com.madlava.probes.ProbeBridge;
import com.madlava.io.RuntimeObservationBridge;
import com.madlava.pools.ObservedExecutorService;
import com.madlava.diagnostics.DiagnosticsRuntime;
import com.madlava.spark.SparkObservationRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Legacy snapshot scheduler retained for compatibility with the older feature-registry path. */
public final class SnapshotScheduler implements AutoCloseable {
    private final RuntimeContext context;
    private final BoundedSnapshotQueue queue;
    private final String version, hash;
    private final ScheduledExecutorService executor;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final JvmMetricsCollector collector = new JvmMetricsCollector();

    public SnapshotScheduler(RuntimeContext context, BoundedSnapshotQueue queue, String version, String hash) {
        this.context = context; this.queue = queue; this.version = version; this.hash = hash;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "madlava-snapshot"); t.setDaemon(true); return t;
        });
    }

    public void start(long intervalSeconds) {
        if (intervalSeconds < 1L) throw new IllegalArgumentException("intervalSeconds must be positive");
        if (closed.get()) throw new IllegalStateException("SnapshotScheduler is closed");
        if (!started.compareAndSet(false, true)) return;
        executor.scheduleWithFixedDelay(() -> emit(false), 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Emission is serialized with close(). The old compare-and-set guard could simply drop the
     * requested final snapshot whenever a periodic build happened to be in progress.
     */
    public synchronized void emit(boolean finalSnapshot) {
        if (closed.get() && !finalSnapshot) return;
        Map<String,Object> data = new LinkedHashMap<>(collector.collect());
        Map<String, com.madlava.core.FeatureState> features = context.featureRegistry().snapshot();
        if (features.containsKey("instanceCounting")) {
            ProbeBridge.Snapshot probes = ProbeBridge.snapshot();
            data.put("instanceCounting", Map.of("successfulOutermostConstructors", probes.constructed(), "accuracy", "SELECTED_CLASSES"));
            Map<String,Object> throwableData = new LinkedHashMap<>();
            throwableData.put("created", probes.throwableCreated());
            throwableData.put("explicitThrows", probes.explicitThrows());
            throwableData.put("propagations", probes.propagations());
            throwableData.put("jfrThrows", probes.jfrThrows());
            throwableData.put("jfrState", probes.jfrState());
            throwableData.put("messageCapture", false);
            throwableData.put("accuracy", "SELECTED_CLASSES");
            data.put("throwables", throwableData);
        }
        if (features.containsKey("streamIo")) {
            RuntimeObservationBridge.Snapshot observation = RuntimeObservationBridge.snapshot();
            data.put("streamIo", Map.of("observedLayers", observation.ioReport(), "physicalAggregation", false));
            data.put("networkIo", Map.of("observedLayers", observation.ioReport(), "endpointAnonymized", true));
            data.put("serialization", Map.of("rootOperations", observation.serializationReport(), "byteAccuracy", "SOURCE_SPECIFIC", "payloadCapture", false));
            data.put("threadPools", ObservedExecutorService.snapshot().report());
        }
        if (features.containsKey("executionSampling")) data.putAll(DiagnosticsRuntime.snapshot());
        if (features.containsKey("spark")) data.put("spark", SparkObservationRegistry.snapshot(Thread.currentThread().getContextClassLoader()));
        Snapshot snapshot = new Snapshot(version, hash, sequence.incrementAndGet(), queue.droppedCount(),
                Instant.now(context.clock()), finalSnapshot, features, data);
        queue.submit(JsonEncoder.encode(snapshot));
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        emit(true);
    }
}
