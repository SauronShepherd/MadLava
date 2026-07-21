package com.madlava.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FeatureRegistry {
    private final Map<String, FeatureState> states = new LinkedHashMap<>();

    public synchronized void register(String id, FeatureState state) {
        if (id == null || id.isBlank() || state == null) throw new IllegalArgumentException("Feature ID and state are required");
        if (states.putIfAbsent(id, state) != null) throw new IllegalArgumentException("Duplicate feature: " + id);
    }

    public synchronized void transition(String id, FeatureState state) {
        if (!states.containsKey(id)) throw new IllegalArgumentException("Unknown feature: " + id);
        states.put(id, state);
    }

    public synchronized Map<String, FeatureState> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }
}
