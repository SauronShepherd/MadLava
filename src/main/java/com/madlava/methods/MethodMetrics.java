package com.madlava.methods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import com.madlava.tracing.TraceEvent;
import com.madlava.tracing.TraceSampler;
import com.madlava.tracing.ArgumentCapture;
import com.madlava.tracing.SafeArgumentRenderer;
import com.madlava.tracing.ArgumentRedactor;

/** Lock-free inclusive method-boundary aggregation. */
public final class MethodMetrics {
    private final MethodRegistry registry;
    private final ConcurrentHashMap<Integer, Counters> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<ArgumentKey, LongAdder>> argumentGroups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LongAdder> droppedArgumentGroups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LongAdder> overflowArgumentInvocations = new ConcurrentHashMap<>();
    private static final int MAX_ARGUMENT_GROUPS_PER_METHOD = 256;
    private final LongAdder suppressedReentrantCallbacks = new LongAdder();
    private volatile Consumer<Map<String,Object>> traceSink;
    private volatile long traceConfigurationVersion;
    private volatile TraceSampler traceSampler = new TraceSampler(1.0);
    private volatile ArgumentCapture argumentCapture = new ArgumentCapture(new SafeArgumentRenderer(), new ArgumentRedactor(null, null), 16);

    public MethodMetrics(MethodRegistry registry) {
        this.registry = registry;
    }

    public void entered(int methodId) {
        if (methodId == MethodRegistry.REJECTED_ID) {
            return;
        }
        counters.computeIfAbsent(methodId, ignored -> new Counters()).invocations.increment();
    }

    public void normalCompletion(int methodId, long durationNanos) {
        if (methodId == MethodRegistry.REJECTED_ID) {
            return;
        }
        Counters values = counters.computeIfAbsent(methodId, ignored -> new Counters());
        values.normalCompletions.increment();
        values.recordDuration(durationNanos);
        emitTrace(methodId, durationNanos);
    }

    public void exceptionalCompletion(int methodId, long durationNanos) {
        if (methodId == MethodRegistry.REJECTED_ID) {
            return;
        }
        Counters values = counters.computeIfAbsent(methodId, ignored -> new Counters());
        values.exceptionalCompletions.increment();
        values.recordDuration(durationNanos);
        emitTrace(methodId, durationNanos);
    }

    public void enableTracing(long configurationVersion, Consumer<Map<String,Object>> sink) { traceConfigurationVersion=configurationVersion; traceSink=sink; }
    public void enableTracing(long configurationVersion, double sampleRate, Consumer<Map<String,Object>> sink) { traceConfigurationVersion=configurationVersion; traceSampler=new TraceSampler(sampleRate); traceSink=sink; }
    public void disableTracing() { traceSink=null; }
    public void configureArgumentCapture(ArgumentCapture capture) { if(capture!=null) argumentCapture=capture; }
    public void traceArguments(int methodId, long durationNanos, Object[] arguments) {
        MethodKey key=registry.key(methodId);
        if(key==null)return;
        try {
            List<String> rendered = argumentCapture.capture(arguments);
            ConcurrentHashMap<ArgumentKey, LongAdder> groups = argumentGroups.computeIfAbsent(methodId, ignored -> new ConcurrentHashMap<>());
            ArgumentKey keyValue = new ArgumentKey(rendered);
            LongAdder group = groups.get(keyValue);
            if(group == null && groups.size() >= MAX_ARGUMENT_GROUPS_PER_METHOD) {
                droppedArgumentGroups.computeIfAbsent(methodId, ignored -> new LongAdder()).increment();
                overflowArgumentInvocations.computeIfAbsent(methodId, ignored -> new LongAdder()).increment();
                return;
            }
            groups.computeIfAbsent(keyValue, ignored -> new LongAdder()).increment();
        }
        catch(Throwable ignored) { }
    }

    private void emitTrace(int methodId, long durationNanos) {
        Consumer<Map<String,Object>> sink=traceSink; MethodKey key=registry.key(methodId);
        if(sink==null||key==null||!traceSampler.sample())return;
        try { sink.accept(TraceEvent.methodCall(traceConfigurationVersion,key.owner(),key.name(),key.descriptor(),durationNanos,null)); }
        catch(Throwable ignored) { }
    }

    public void suppressedReentrantCallback() {
        suppressedReentrantCallbacks.increment();
    }

    public Map<String, Object> report() {
        List<Map<String, Object>> methods = new ArrayList<>();
        for (Map.Entry<Integer, Counters> entry : counters.entrySet()) {
            MethodKey key = registry.key(entry.getKey());
            if (key == null) {
                continue;
            }
            Counters values = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>(key.report());
            long completions = values.normalCompletions.sum() + values.exceptionalCompletions.sum();
            long total = values.totalDurationNanos.sum();
            item.put("invocations", values.invocations.sum());
            item.put("normalCompletions", values.normalCompletions.sum());
            item.put("exceptionalCompletions", values.exceptionalCompletions.sum());
            item.put("timedCompletions", completions);
            item.put("totalDurationNanos", total);
            item.put("minimumDurationNanos", completions == 0 ? 0 : values.minimumDurationNanos.get());
            item.put("maximumDurationNanos", completions == 0 ? 0 : values.maximumDurationNanos.get());
            item.put("averageDurationNanos", completions == 0 ? 0 : total / completions);
            item.put("timingSemantics", "INCLUSIVE_ELAPSED_SYSTEM_NANO_TIME");
            ConcurrentHashMap<ArgumentKey, LongAdder> groups = argumentGroups.get(entry.getKey());
            if (groups != null && !groups.isEmpty()) {
                List<Map<String,Object>> argumentReports = new ArrayList<>();
                groups.forEach((arguments, count) -> { Map<String,Object> group = new LinkedHashMap<>(); group.put("arguments", arguments.arguments()); group.put("invocations", count.sum()); argumentReports.add(group); });
                argumentReports.sort(Comparator.comparingLong((Map<String,Object> group) -> ((Number) group.get("invocations")).longValue()).reversed().thenComparing(Object::toString));
                item.put("argumentGroups", argumentReports);
                item.put("droppedArgumentGroups", droppedArgumentGroups.getOrDefault(entry.getKey(), new LongAdder()).sum());
                item.put("overflowArgumentInvocations", overflowArgumentInvocations.getOrDefault(entry.getKey(), new LongAdder()).sum());
            }
            methods.add(item);
        }
        methods.sort(Comparator
                .comparingLong((Map<String, Object> value) -> ((Number) value.get("totalDurationNanos")).longValue())
                .reversed()
                .thenComparing(value -> String.valueOf(value.get("owner")))
                .thenComparing(value -> String.valueOf(value.get("method"))));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("state", "RUNNING");
        report.put("registeredMethods", registry.size());
        report.put("maximumMethods", registry.maximumEntries());
        report.put("droppedMethodRegistrations", registry.droppedRegistrations());
        report.put("suppressedReentrantCallbacks", suppressedReentrantCallbacks.sum());
        report.put("methods", methods);
        report.put("limitations", List.of(
                "Durations are inclusive; nested method durations overlap.",
                "Counts describe selected method boundaries, not physical bytes or CPU samples.",
                "Arguments, return values, payloads and exception messages are never retained."));
        return report;
    }

    public void reset() {
        counters.clear();
    }

    private static final class Counters {
        private final LongAdder invocations = new LongAdder();
        private final LongAdder normalCompletions = new LongAdder();
        private final LongAdder exceptionalCompletions = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final LongAccumulator minimumDurationNanos = new LongAccumulator(Long::min, Long.MAX_VALUE);
        private final LongAccumulator maximumDurationNanos = new LongAccumulator(Long::max, Long.MIN_VALUE);

        private void recordDuration(long rawDuration) {
            long duration = Math.max(0L, rawDuration);
            totalDurationNanos.add(duration);
            minimumDurationNanos.accumulate(duration);
            maximumDurationNanos.accumulate(duration);
        }
    }
}
