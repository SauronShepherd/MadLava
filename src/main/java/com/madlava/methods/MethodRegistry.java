package com.madlava.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
    private final Map<Integer, ReservationState> states = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final LongAdder droppedRegistrations = new LongAdder();

    public MethodRegistry(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        this.maximumEntries = maximumEntries;
    }

    /** Permanent registration used by direct callers/tests. */
    public int register(MethodKey key) {
        Reservation reservation = reserve(key);
        if (reservation.id == REJECTED_ID) return REJECTED_ID;
        commit(reservation);
        return reservation.id;
    }

    /**
     * Reserve an ID while a class transformation is still provisional. Failed transformations
     * can roll the reservation back without consuming bounded registry capacity.
     */
    public synchronized Reservation reserve(MethodKey key) {
        if (key == null) throw new IllegalArgumentException("key");
        Integer existing = ids.get(key);
        if (existing != null) {
            ReservationState state = states.get(existing);
            if (state == null) {
                state = new ReservationState(key, true);
                states.put(existing, state);
            }
            state.pending++;
            return new Reservation(existing, key);
        }
        if (ids.size() >= maximumEntries) {
            droppedRegistrations.increment();
            return new Reservation(REJECTED_ID, key);
        }
        int id = nextId.getAndIncrement();
        ids.put(key, id);
        keys.put(id, key);
        ReservationState state = new ReservationState(key, false);
        state.pending = 1;
        states.put(id, state);
        return new Reservation(id, key);
    }

    public synchronized void commit(Reservation reservation) {
        if (reservation == null || reservation.id == REJECTED_ID) return;
        ReservationState state = states.get(reservation.id);
        if (state == null || !state.key.equals(reservation.key)) return;
        if (state.pending > 0) state.pending--;
        state.committed = true;
    }

    public synchronized void rollback(Reservation reservation) {
        if (reservation == null || reservation.id == REJECTED_ID) return;
        ReservationState state = states.get(reservation.id);
        if (state == null || !state.key.equals(reservation.key)) return;
        if (state.pending > 0) state.pending--;
        if (!state.committed && state.pending == 0) {
            ids.remove(state.key, reservation.id);
            keys.remove(reservation.id, state.key);
            states.remove(reservation.id);
        }
    }

    public MethodKey key(int id) { return keys.get(id); }
    public int size() { return ids.size(); }
    public int maximumEntries() { return maximumEntries; }
    public long droppedRegistrations() { return droppedRegistrations.sum(); }
    void resetDroppedRegistrations() { droppedRegistrations.reset(); }

    public List<Map.Entry<Integer, MethodKey>> entries() {
        List<Map.Entry<Integer, MethodKey>> result = new ArrayList<>(keys.entrySet());
        result.sort(Comparator.comparingInt(Map.Entry::getKey));
        return Collections.unmodifiableList(result);
    }

    public static final class Reservation {
        private final int id;
        private final MethodKey key;
        private Reservation(int id, MethodKey key) { this.id = id; this.key = key; }
        public int id() { return id; }
    }
    private static final class ReservationState {
        private final MethodKey key;
        private boolean committed;
        private int pending;
        private ReservationState(MethodKey key, boolean committed) { this.key = key; this.committed = committed; }
    }
}
