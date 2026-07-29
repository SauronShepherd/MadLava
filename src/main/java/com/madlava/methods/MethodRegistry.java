package com.madlava.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Bounded ID registry that never retains application Class or ClassLoader objects. */
public final class MethodRegistry {
    public static final int REJECTED_ID = 0;

    private final int maximumEntries;
    private final ConcurrentHashMap<MethodKey, Integer> ids = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, MethodKey> keys = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final LongAdder droppedRegistrations = new LongAdder();

    public MethodRegistry(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
    }

    public int register(MethodKey key) {
        Integer existing = ids.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            existing = ids.get(key);
            if (existing != null) {
                return existing;
            }
            if (ids.size() >= maximumEntries) {
                droppedRegistrations.increment();
                return REJECTED_ID;
            }
            int id = nextId.getAndIncrement();
            ids.put(key, id);
            keys.put(id, key);
            return id;
        }
    }

    public MethodKey key(int id) {
        return keys.get(id);
    }

    public int size() {
        return ids.size();
    }

    public int maximumEntries() {
        return maximumEntries;
    }

    public long droppedRegistrations() {
        return droppedRegistrations.sum();
    }

    public List<Map.Entry<Integer, MethodKey>> entries() {
        List<Map.Entry<Integer, MethodKey>> result = new ArrayList<>(keys.entrySet());
        result.sort(Comparator.comparingInt(Map.Entry::getKey));
        return Collections.unmodifiableList(result);
    }
}
