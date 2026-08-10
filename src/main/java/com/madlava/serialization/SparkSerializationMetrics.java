package com.madlava.serialization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Bounded serializer-specific aggregation with explicit byte-accuracy semantics. */
public final class SparkSerializationMetrics {
    private final int maximumGroups;
    private final ConcurrentHashMap<GroupKey, Counters> groups = new ConcurrentHashMap<>();
    private final Object registrationLock = new Object();
    private final LongAdder droppedGroups = new LongAdder();
    private final LongAdder suppressedNestedOperations = new LongAdder();
    private final LongAdder bridgeFailures = new LongAdder();

    public SparkSerializationMetrics(int maximumGroups) {
        if (maximumGroups < 1) {
            throw new IllegalArgumentException("maximumGroups must be positive");
        }
        this.maximumGroups = maximumGroups;
    }

    public void record(
            SparkSerializationTarget target,
            String rootClass,
            boolean success,
            long durationNanos,
            long bytes,
            ByteAccuracy accuracy,
            long nestedSuppressed) {
        GroupKey candidate = new GroupKey(
                target.owner(),
                target.operation().name(),
                target.layer().name(),
                safeRootClass(rootClass),
                accuracy.name());
        // Section-level suppression is independent of whether this root operation fits in the
        // bounded group table. Otherwise pressure on group cardinality makes suppression itself
        // disappear from the report.
        if (nestedSuppressed > 0L) {
            suppressedNestedOperations.add(nestedSuppressed);
        }
        Counters counters = countersFor(candidate);
        if (counters == null) {
            return;
        }
        counters.operations.increment();
        if (success) {
            counters.successfulOperations.increment();
        } else {
            counters.failedOperations.increment();
        }
        counters.recordDuration(durationNanos);
        if (bytes >= 0L) {
            counters.observedBytes.add(bytes);
            counters.operationsWithBytes.increment();
        }
        if (nestedSuppressed > 0L) {
            counters.nestedOperationsSuppressed.add(nestedSuppressed);
        }
    }

    public void bridgeFailure() {
        bridgeFailures.increment();
    }

    public Map<String, Object> report(SparkSerializationPlan plan) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<GroupKey, Counters> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            Counters values = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("implementation", key.implementation);
            item.put("operation", key.operation);
            item.put("layer", key.layer);
            item.put("rootClass", key.rootClass);
            item.put("byteAccuracy", key.byteAccuracy);
            // Outcomes/detail counters are updated after operations. Read them first and the
            // parent operation count last to avoid transient impossible live snapshots.
            long successful = values.successfulOperations.sum();
            long failed = values.failedOperations.sum();
            long total = values.totalDurationNanos.sum();
            long operationsWithBytes = values.operationsWithBytes.sum();
            long observedBytes = values.observedBytes.sum();
            long nested = values.nestedOperationsSuppressed.sum();
            long operations = values.operations.sum();
            item.put("successfulOperations", successful);
            item.put("failedOperations", failed);
            item.put("totalDurationNanos", total);
            item.put("minimumDurationNanos", operations == 0 ? 0 : values.minimumDurationNanos.get());
            item.put("maximumDurationNanos", operations == 0 ? 0 : values.maximumDurationNanos.get());
            item.put("averageDurationNanos", operations == 0 ? 0 : total / operations);
            item.put("operationsWithObservedBytes", operationsWithBytes);
            item.put("observedBytes", observedBytes);
            item.put("nestedOperationsSuppressed", nested);
            item.put("operations", operations);
            results.add(item);
        }
        results.sort(Comparator
                .comparingLong((Map<String, Object> value) -> ((Number) value.get("totalDurationNanos")).longValue())
                .reversed()
                .thenComparing(value -> String.valueOf(value.get("implementation")))
                .thenComparing(value -> String.valueOf(value.get("operation"))));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("state", "RUNNING");
        report.put("maximumGroups", maximumGroups);
        report.put("activeGroups", groups.size());
        report.put("droppedGroups", droppedGroups.sum());
        report.put("suppressedNestedOperations", suppressedNestedOperations.sum());
        report.put("bridgeFailures", bridgeFailures.sum());
        report.put("runtime", SparkRuntimeInfo.detect());
        report.put("groups", results);
        report.put("coverage", plan.coverageReport());
        report.put("limitations", List.of(
                "Boundary and stream observations may describe overlapping work and must not be summed as independent physical traffic.",
                "Byte counts are present only when the selected boundary exposes an exact ByteBuffer size.",
                "UNAVAILABLE is distinct from an observed byte count of zero.",
                "Root classes are bounded class names only; no object values or payloads are retained."));
        return report;
    }

    public void reset() {
        groups.clear();
        droppedGroups.reset();
        suppressedNestedOperations.reset();
        bridgeFailures.reset();
    }

    private Counters countersFor(GroupKey candidate) {
        Counters existing = groups.get(candidate);
        if (existing != null) {
            return existing;
        }
        synchronized (registrationLock) {
            existing = groups.get(candidate);
            if (existing != null) {
                return existing;
            }
            if (groups.size() >= maximumGroups) {
                droppedGroups.increment();
                return null;
            }
            Counters created = new Counters();
            groups.put(candidate, created);
            return created;
        }
    }

    private static String safeRootClass(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static final class GroupKey {
        private final String implementation;
        private final String operation;
        private final String layer;
        private final String rootClass;
        private final String byteAccuracy;

        private GroupKey(String implementation, String operation, String layer, String rootClass, String byteAccuracy) {
            this.implementation = implementation;
            this.operation = operation;
            this.layer = layer;
            this.rootClass = rootClass;
            this.byteAccuracy = byteAccuracy;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupKey)) {
                return false;
            }
            GroupKey that = (GroupKey) other;
            return implementation.equals(that.implementation)
                    && operation.equals(that.operation)
                    && layer.equals(that.layer)
                    && rootClass.equals(that.rootClass)
                    && byteAccuracy.equals(that.byteAccuracy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(implementation, operation, layer, rootClass, byteAccuracy);
        }
    }

    private static final class Counters {
        private final LongAdder operations = new LongAdder();
        private final LongAdder successfulOperations = new LongAdder();
        private final LongAdder failedOperations = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final LongAdder observedBytes = new LongAdder();
        private final LongAdder operationsWithBytes = new LongAdder();
        private final LongAdder nestedOperationsSuppressed = new LongAdder();
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
