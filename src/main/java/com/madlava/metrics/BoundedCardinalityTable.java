package com.madlava.metrics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded key aggregation with a synthetic overflow bucket. */
public final class BoundedCardinalityTable {
    private static final String OVERFLOW_KEY = "other";
    private final int maximum;
    private final Map<String, Long> values = new LinkedHashMap<>();
    private long overflow;

    public BoundedCardinalityTable(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Maximum must be positive");
        this.maximum = maximum;
    }

    public synchronized void add(String key, long value) {
        if (values.containsKey(key)) {
            values.put(key, values.get(key) + value);
        } else if (values.size() < maximum) {
            values.put(key, value);
        } else {
            overflow += value;
        }
    }

    public synchronized Map<String, Long> snapshot() {
        Map<String, Long> copy = new LinkedHashMap<>(values);
        if (overflow != 0L) {
            // "other" is a legal observed key. Never overwrite its real count when the synthetic
            // overflow bucket is emitted; combine them so total accounting remains lossless.
            copy.merge(OVERFLOW_KEY, overflow, Long::sum);
        }
        return Collections.unmodifiableMap(copy);
    }
}
