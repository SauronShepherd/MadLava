package com.madlava.methods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import com.madlava.tracing.TraceEvent;
import com.madlava.tracing.TraceSampler;
import com.madlava.tracing.ArgumentCapture;
import com.madlava.tracing.SafeArgumentRenderer;
import com.madlava.tracing.ArgumentRedactor;
import com.madlava.tracing.ArgumentCanonicalizer;

/** Lock-free inclusive method-boundary aggregation. */
public final class MethodMetrics {
    private final MethodRegistry registry;
    private final AtomicReferenceArray<Counters> counters;
    private final ConcurrentHashMap<Integer, Counters> detachedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<ArgumentKey, LongAdder>> argumentGroups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LongAdder> droppedArgumentGroups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LongAdder> overflowArgumentInvocations = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_ARGUMENT_GROUPS_PER_METHOD = 256;
    private final int maxArgumentGroupsPerMethod;
    private final LongAdder suppressedReentrantCallbacks = new LongAdder();
    private final LongAdder failedTraceDeliveries = new LongAdder();
    private volatile TraceConfiguration traceConfiguration;
    private volatile ArgumentCapture argumentCapture = new ArgumentCapture(new SafeArgumentRenderer(), new ArgumentRedactor(null, null), 16);
    private volatile ArgumentCanonicalizer argumentCanonicalizer = new ArgumentCanonicalizer();

    public MethodMetrics(MethodRegistry registry) {
        this(registry, DEFAULT_MAX_ARGUMENT_GROUPS_PER_METHOD);
    }
    public MethodMetrics(MethodRegistry registry, int maxArgumentGroupsPerMethod) {
        this.registry = registry;
        if(maxArgumentGroupsPerMethod<1)throw new IllegalArgumentException("maxArgumentGroupsPerMethod must be positive");
        this.maxArgumentGroupsPerMethod=maxArgumentGroupsPerMethod;
        this.counters = new AtomicReferenceArray<>(registry == null ? 1 : registry.maximumEntries() + 1);
    }

    private Counters countersFor(int methodId) {
        if (registry == null) {
            return detachedCounters.computeIfAbsent(methodId, ignored -> new Counters());
        }
        Counters existing = counters.get(methodId);
        if (existing != null) return existing;
        Counters created = new Counters();
        if (counters.compareAndSet(methodId, null, created)) return created;
        return counters.get(methodId);
    }

    public void entered(int methodId) {
        if (methodId == MethodRegistry.REJECTED_ID) return;
        countersFor(methodId).invocations.increment();
    }

    public void normalCompletion(int methodId, long durationNanos) {
        if (methodId == MethodRegistry.REJECTED_ID) return;
        Counters values = countersFor(methodId);
        values.recordDuration(durationNanos);
        values.normalCompletions.increment();
        emitTrace(methodId, durationNanos);
    }

    public void exceptionalCompletion(int methodId, long durationNanos) {
        if (methodId == MethodRegistry.REJECTED_ID) return;
        Counters values = countersFor(methodId);
        values.recordDuration(durationNanos);
        values.exceptionalCompletions.increment();
        emitTrace(methodId, durationNanos);
    }

    public void enableTracing(long configurationVersion, Consumer<Map<String,Object>> sink) { enableTracing(configurationVersion, 1.0, sink); }
    public void enableTracing(long configurationVersion, double sampleRate, Consumer<Map<String,Object>> sink) { traceConfiguration = sink == null ? null : new TraceConfiguration(configurationVersion, new TraceSampler(sampleRate), sink); }
    public void disableTracing() { traceConfiguration=null; }
    public void configureArgumentCapture(ArgumentCapture capture) { if(capture!=null) argumentCapture=capture; }
    public void traceArguments(int methodId, long durationNanos, Object[] arguments) {
        MethodKey key=registry.key(methodId);
        if(key==null)return;
        try {
            List<String> rendered = argumentCanonicalizer.canonicalize(arguments);
            ConcurrentHashMap<ArgumentKey, LongAdder> groups = argumentGroups.computeIfAbsent(methodId, ignored -> new ConcurrentHashMap<>());
            ArgumentKey keyValue = new ArgumentKey(rendered);
            LongAdder group = groups.get(keyValue);
            if (group == null) {
                synchronized (groups) {
                    group = groups.get(keyValue);
                    if (group == null) {
                        if (groups.size() >= maxArgumentGroupsPerMethod) {
                            droppedArgumentGroups.computeIfAbsent(methodId, ignored -> new LongAdder()).increment();
                            overflowArgumentInvocations.computeIfAbsent(methodId, ignored -> new LongAdder()).increment();
                            return;
                        }
                        group = new LongAdder();
                        groups.put(keyValue, group);
                    }
                }
            }
            group.increment();
        } catch(Throwable ignored) { }
    }

    private void emitTrace(int methodId, long durationNanos) {
        TraceConfiguration tracing = traceConfiguration;
        if(tracing==null)return;
        MethodKey key=registry == null ? null : registry.key(methodId);
        if(key==null||!tracing.sampler.sample())return;
        try { tracing.sink.accept(TraceEvent.methodCall(tracing.configurationVersion,key.owner(),key.name(),key.descriptor(),durationNanos,null)); }
        catch(Throwable ignored) { failedTraceDeliveries.increment(); }
    }

    public void suppressedReentrantCallback() { suppressedReentrantCallbacks.increment(); }

    public Map<String, Object> report() {
        List<Map<String, Object>> methods = new ArrayList<>();
        if (registry != null) {
            for (Map.Entry<Integer, MethodKey> entry : registry.entries()) {
                int methodId = entry.getKey();
                Counters values = counters.get(methodId);
                if (values == null) continue;
                MethodKey key = entry.getValue();
                Map<String, Object> item = new LinkedHashMap<>(key.report());
                ConcurrentHashMap<ArgumentKey, LongAdder> groups = argumentGroups.get(methodId);
                if (groups != null && !groups.isEmpty()) {
                    List<Map<String,Object>> argumentReports = new ArrayList<>();
                    groups.forEach((arguments, count) -> { Map<String,Object> group = new LinkedHashMap<>(); group.put("arguments", arguments.arguments()); group.put("invocations", count.sum()); argumentReports.add(group); });
                    argumentReports.sort(Comparator.comparingLong((Map<String,Object> group) -> ((Number) group.get("invocations")).longValue()).reversed().thenComparing(Object::toString));
                    item.put("argumentGroups", argumentReports);
                    LongAdder dropped = droppedArgumentGroups.get(methodId);
                    LongAdder overflow = overflowArgumentInvocations.get(methodId);
                    item.put("droppedArgumentGroups", dropped == null ? 0L : dropped.sum());
                    item.put("overflowArgumentInvocations", overflow == null ? 0L : overflow.sum());
                }
                long normal = values.normalCompletions.sum();
                long exceptional = values.exceptionalCompletions.sum();
                long completions = normal + exceptional;
                long total = values.totalDurationNanos.sum();
                item.put("normalCompletions", normal);
                item.put("exceptionalCompletions", exceptional);
                item.put("timedCompletions", completions);
                item.put("totalDurationNanos", total);
                item.put("minimumDurationNanos", completions == 0 ? 0 : values.minimumDurationNanos.get());
                item.put("maximumDurationNanos", completions == 0 ? 0 : values.maximumDurationNanos.get());
                item.put("averageDurationNanos", completions == 0 ? 0 : total / completions);
                item.put("timingSemantics", "INCLUSIVE_ELAPSED_SYSTEM_NANO_TIME");
                item.put("invocations", values.invocations.sum());
                methods.add(item);
            }
        }
        methods.sort(Comparator.comparingLong((Map<String, Object> value) -> ((Number) value.get("totalDurationNanos")).longValue()).reversed().thenComparing(value -> String.valueOf(value.get("owner"))).thenComparing(value -> String.valueOf(value.get("method"))));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("state", "RUNNING");
        report.put("registeredMethods", registry == null ? 0 : registry.size());
        report.put("maximumMethods", registry == null ? 0 : registry.maximumEntries());
        report.put("droppedMethodRegistrations", registry == null ? 0L : registry.droppedRegistrations());
        report.put("suppressedReentrantCallbacks", suppressedReentrantCallbacks.sum());
        report.put("failedTraceDeliveries", failedTraceDeliveries.sum());
        report.put("methods", methods);
        report.put("limitations", List.of("Durations are inclusive; nested method durations overlap.", "Counts describe selected method boundaries, not physical bytes or CPU samples.", "Raw arguments, return values, payloads and exception messages are never retained; COUNT_BY_ARGS stores bounded type shapes and per-run salted scalar fingerprints."));
        return report;
    }

    public void reset() {
        for (int methodId = 1; methodId < counters.length(); methodId++) counters.set(methodId, null);
        detachedCounters.clear();
        argumentGroups.clear();
        droppedArgumentGroups.clear();
        overflowArgumentInvocations.clear();
        suppressedReentrantCallbacks.reset();
        failedTraceDeliveries.reset();
        if (registry != null) registry.resetDroppedRegistrations();
    }

    private static final class TraceConfiguration {
        private final long configurationVersion; private final TraceSampler sampler; private final Consumer<Map<String,Object>> sink;
        private TraceConfiguration(long configurationVersion, TraceSampler sampler, Consumer<Map<String,Object>> sink) { this.configurationVersion = configurationVersion; this.sampler = sampler; this.sink = sink; }
    }

    private static final class Counters {
        private final LongAdder invocations = new LongAdder();
        private final LongAdder normalCompletions = new LongAdder();
        private final LongAdder exceptionalCompletions = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final LongAccumulator minimumDurationNanos = new LongAccumulator(Long::min, Long.MAX_VALUE);
        private final LongAccumulator maximumDurationNanos = new LongAccumulator(Long::max, Long.MIN_VALUE);
        private void recordDuration(long rawDuration) { long duration = Math.max(0L, rawDuration); totalDurationNanos.add(duration); minimumDurationNanos.accumulate(duration); maximumDurationNanos.accumulate(duration); }
    }
}