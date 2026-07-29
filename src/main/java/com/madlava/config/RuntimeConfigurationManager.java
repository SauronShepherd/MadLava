package com.madlava.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Atomic, immutable runtime configuration state. Invalid reloads never replace the active value. */
public final class RuntimeConfigurationManager {
    public interface Listener { void applied(ConfigurationState previous, ConfigurationState current); }
    public interface RejectionListener { void rejected(ConfigurationState current, String reason); }
    public static final class ConfigurationState {
        private final Map<String, Object> values;
        private final long version;
        private final String hash;
        private ConfigurationState(EffectiveConfiguration effective, long version) {
            this.values = effective.values(); this.version = version; this.hash = effective.hash();
        }
        public Map<String, Object> values() { return values; }
        public long version() { return version; }
        public String hash() { return hash; }
    }
    public static final class UpdateResult {
        private final boolean applied; private final String reason; private final ConfigurationState state;
        private UpdateResult(boolean applied, String reason, ConfigurationState state) { this.applied=applied;this.reason=reason;this.state=state; }
        public boolean applied(){return applied;} public String reason(){return reason;} public ConfigurationState state(){return state;}
    }

    private final ConfigurationResolver resolver;
    private final String sourcePath;
    private final AtomicReference<ConfigurationState> current;
    private final AtomicLong version = new AtomicLong();
    private final java.util.concurrent.CopyOnWriteArrayList<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<RejectionListener> rejectionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final LongAdder successfulReloads = new LongAdder(), failedReloads = new LongAdder();
    private volatile long lastReloadEpochMillis;

    public RuntimeConfigurationManager(ConfigurationResolver resolver, Map<String, ?> initial, String source) {
        this.resolver = resolver;
        this.sourcePath = source == null ? "" : source;
        EffectiveConfiguration effective = resolver.resolve(initial, Collections.emptyMap(), source);
        current = new AtomicReference<>(new ConfigurationState(effective, version.incrementAndGet()));
    }
    public ConfigurationState current() { return current.get(); }
    public UpdateResult reloadSource() {
        if (sourcePath.isBlank()) return new UpdateResult(false, "NO_FILE_SOURCE", current.get());
        return reloadJson(java.nio.file.Paths.get(sourcePath), Collections.emptyMap());
    }
    public void addListener(Listener listener) { if (listener != null) listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public void addRejectionListener(RejectionListener listener) { if (listener != null) rejectionListeners.add(listener); }
    public void removeRejectionListener(RejectionListener listener) { rejectionListeners.remove(listener); }
    public long successfulReloads() { return successfulReloads.sum(); }
    public long failedReloads() { return failedReloads.sum(); }
    public long lastReloadEpochMillis() { return lastReloadEpochMillis; }
    public UpdateResult reload(Map<String, ?> fileValues, Map<String, String> overrides, String source) {
        try {
            EffectiveConfiguration effective = resolver.resolve(fileValues, overrides, source);
            ConfigurationState previous = current.get();
            ConfigurationState next = new ConfigurationState(effective, version.incrementAndGet());
            current.set(next);
            successfulReloads.increment(); lastReloadEpochMillis = System.currentTimeMillis();
            for (Listener listener : listeners) { try { listener.applied(previous, next); } catch (Throwable ignored) { } }
            return new UpdateResult(true, "APPLIED", next);
        } catch (RuntimeException failure) {
            failedReloads.increment(); lastReloadEpochMillis = System.currentTimeMillis();
            String reason = failure.getMessage() == null ? "INVALID_CONFIGURATION" : failure.getMessage();
            for (RejectionListener listener : rejectionListeners) { try { listener.rejected(current.get(), reason); } catch (Throwable ignored) { } }
            return new UpdateResult(false, reason, current.get());
        }
    }
    public UpdateResult reloadJson(Path path, Map<String, String> overrides) {
        try {
            Object parsed = SimpleJsonParser.parse(Files.readString(path));
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Configuration root must be an object");
            Map<String, Object> values = new LinkedHashMap<>();
            flatten("", (Map<?, ?>) parsed, values);
            return reload(values, overrides, path.toAbsolutePath().normalize().toString());
        } catch (Exception failure) {
            failedReloads.increment(); lastReloadEpochMillis = System.currentTimeMillis();
            for (RejectionListener listener : rejectionListeners) { try { listener.rejected(current.get(), "INVALID_CONFIGURATION"); } catch (Throwable ignored) { } }
            return new UpdateResult(false, "INVALID_CONFIGURATION", current.get());
        }
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, Object> target) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?>) flatten(path, (Map<?, ?>) value, target);
            else if (value instanceof Iterable<?> && (path.endsWith(".includes") || path.endsWith(".excludes"))) {
                StringBuilder joined = new StringBuilder();
                for (Object item : (Iterable<?>) value) { if (joined.length() > 0) joined.append(';'); joined.append(item); }
                target.put(path, joined.toString());
            } else target.put(path, value);
        });
    }
}
