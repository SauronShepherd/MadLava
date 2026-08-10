package com.madlava.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Small synchronized weak-key map with identity semantics.
 *
 * <p>Unlike {@link java.util.WeakHashMap}, keys are compared with {@code ==} and
 * hashed with {@link System#identityHashCode(Object)}. This is important in agent
 * code because calling application-defined {@code equals}/{@code hashCode} from a
 * transformer or reporter can execute arbitrary observed-application code.</p>
 */
public final class WeakIdentityMap<K, V> {
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();
    private final Map<IdentityWeakReference<K>, V> values = new HashMap<>();

    public synchronized V get(K key) {
        if (key == null) return null;
        expungeStaleEntries();
        return values.get(new IdentityWeakReference<>(key, null));
    }

    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> factory) {
        if (key == null || factory == null) throw new IllegalArgumentException();
        expungeStaleEntries();
        IdentityWeakReference<K> lookup = new IdentityWeakReference<>(key, null);
        V existing = values.get(lookup);
        if (existing != null) return existing;
        V created = factory.apply(key);
        values.put(new IdentityWeakReference<>(key, queue), created);
        return created;
    }

    public synchronized V putIfAbsent(K key, V value) {
        if (key == null || value == null) throw new IllegalArgumentException();
        expungeStaleEntries();
        IdentityWeakReference<K> lookup = new IdentityWeakReference<>(key, null);
        V existing = values.get(lookup);
        if (existing != null) return existing;
        values.put(new IdentityWeakReference<>(key, queue), value);
        return null;
    }

    public synchronized int size() {
        expungeStaleEntries();
        return values.size();
    }

    public synchronized void clear() {
        values.clear();
        while (queue.poll() != null) { /* drain */ }
    }

    @SuppressWarnings("unchecked")
    private void expungeStaleEntries() {
        IdentityWeakReference<K> stale;
        while ((stale = (IdentityWeakReference<K>) queue.poll()) != null) values.remove(stale);
    }

    private static final class IdentityWeakReference<T> extends WeakReference<T> {
        private final int hash;
        private IdentityWeakReference(T referent, ReferenceQueue<T> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityWeakReference<?>)) return false;
            Object left = get();
            Object right = ((IdentityWeakReference<?>) other).get();
            return left != null && left == right;
        }
    }
}
