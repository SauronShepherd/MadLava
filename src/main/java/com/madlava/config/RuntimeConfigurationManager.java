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
    /** Returns null when the transition is live-applicable, otherwise a rejection reason. */
    public interface TransitionValidator { String validate(ConfigurationState previous, ConfigurationState proposed); }
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
    private final Map<String, String> persistentOverrides;
    private final AtomicReference<ConfigurationState> current;
    private final AtomicLong version = new AtomicLong();
    private final java.util.concurrent.CopyOnWriteArrayList<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<RejectionListener> rejectionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<TransitionValidator> transitionValidators = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final LongAdder successfulReloads = new LongAdder(), failedReloads = new LongAdder(), listenerFailures = new LongAdder();
    private volatile long lastReloadEpochMillis;

    public RuntimeConfigurationManager(ConfigurationResolver resolver, Map<String, ?> initial, String source) {
        this(resolver, initial, source, Collections.emptyMap());
    }
    public RuntimeConfigurationManager(ConfigurationResolver resolver, Map<String, ?> initial, String source,
                                       Map<String, String> persistentOverrides) {
        this.resolver = resolver;
        this.sourcePath = source == null ? "" : source;
        this.persistentOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(
                persistentOverrides == null ? Collections.emptyMap() : persistentOverrides));
        EffectiveConfiguration effective = resolver.resolve(initial, this.persistentOverrides, source);
        current = new AtomicReference<>(new ConfigurationState(effective, version.incrementAndGet()));
    }
    public ConfigurationState current() { return current.get(); }
    public Map<String,Object> redactedValues() { return resolver.redacted(current.get().values()); }
    public UpdateResult reloadSource() {
        if (sourcePath.isBlank()) return new UpdateResult(false, "NO_FILE_SOURCE", current.get());
        return reloadJson(java.nio.file.Paths.get(sourcePath), Collections.emptyMap());
    }
    public void addListener(Listener listener) { if (listener != null) listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public void addRejectionListener(RejectionListener listener) { if (listener != null) rejectionListeners.add(listener); }
    public void removeRejectionListener(RejectionListener listener) { rejectionListeners.remove(listener); }
    public void addTransitionValidator(TransitionValidator validator) { if (validator != null) transitionValidators.add(validator); }
    public void removeTransitionValidator(TransitionValidator validator) { transitionValidators.remove(validator); }
    public long successfulReloads() { return successfulReloads.sum(); }
    public long failedReloads() { return failedReloads.sum(); }
    public long listenerFailures() { return listenerFailures.sum(); }
    public long lastReloadEpochMillis() { return lastReloadEpochMillis; }
    public synchronized UpdateResult reload(Map<String, ?> fileValues, Map<String, String> overrides, String source) {
        try {
            Map<String,String> effectiveOverrides = new LinkedHashMap<>(persistentOverrides);
            if (overrides != null) effectiveOverrides.putAll(overrides);
            EffectiveConfiguration effective = resolver.resolve(fileValues, effectiveOverrides, source);
            ConfigurationState previous = current.get();
            lastReloadEpochMillis = System.currentTimeMillis();
            if (previous.values().equals(effective.values())) {
                // A watcher start, duplicate filesystem event, or file touch must not create a
                // fictitious configuration version or trigger expensive listeners/retransforms.
                successfulReloads.increment();
                return new UpdateResult(true, "UNCHANGED", previous);
            }
            // Build a proposed state without consuming a public version. Rejected transitions
            // must not create gaps or make a configuration version appear to have been active.
            ConfigurationState proposed = new ConfigurationState(effective, previous.version() + 1);
            for (TransitionValidator validator : transitionValidators) {
                String rejection;
                try { rejection = validator.validate(previous, proposed); }
                catch (Throwable failure) { rejection = "CONFIGURATION_TRANSITION_VALIDATION_FAILED"; }
                if (rejection != null && !rejection.isBlank()) {
                    failedReloads.increment();
                    for (RejectionListener listener : rejectionListeners) {
                        try { listener.rejected(previous, rejection); } catch (Throwable ignored) { }
                    }
                    return new UpdateResult(false, rejection, previous);
                }
            }
            long nextVersion = version.incrementAndGet();
            ConfigurationState next = new ConfigurationState(effective, nextVersion);
            current.set(next);
            successfulReloads.increment();
            boolean listenerFailed = false;
            for (Listener listener : listeners) {
                try { listener.applied(previous, next); }
                catch (Throwable ignored) { listenerFailures.increment(); listenerFailed = true; }
            }
            // Configuration state is committed, but a failed live side effect (for example output
            // rotation or retransformation) must not be silently presented as a clean application.
            return new UpdateResult(true, listenerFailed ? "APPLIED_WITH_LISTENER_FAILURE" : "APPLIED", next);
        } catch (RuntimeException failure) {
            failedReloads.increment(); lastReloadEpochMillis = System.currentTimeMillis();
            String reason = failure.getMessage() == null ? "INVALID_CONFIGURATION" : failure.getMessage();
            for (RejectionListener listener : rejectionListeners) { try { listener.rejected(current.get(), reason); } catch (Throwable ignored) { } }
            return new UpdateResult(false, reason, current.get());
        }
    }
    public UpdateResult reloadJson(Path path, Map<String, String> overrides) {
        try {
            Object parsed = SimpleJsonParser.parse(ConfigurationFileReader.read(path));
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Configuration root must be an object");
            Map<String, Object> values = new LinkedHashMap<>();
            flatten("", (Map<?, ?>) parsed, values);
            return reload(values, overrides, path.toAbsolutePath().normalize().toString());
        } catch (Exception failure) {
            failedReloads.increment(); lastReloadEpochMillis = System.currentTimeMillis();
            String reason = failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "INVALID_CONFIGURATION" : failure.getMessage();
            for (RejectionListener listener : rejectionListeners) { try { listener.rejected(current.get(), reason); } catch (Throwable ignored) { } }
            return new UpdateResult(false, reason, current.get());
        }
    }

    static void flatten(String prefix, Map<?, ?> source, Map<String, Object> target) {
        source.forEach((key, value) -> {
            String component = String.valueOf(key);
            // JSON configuration has one documented nested schema. Literal dotted keys would
            // be interpreted by this runtime flattener but ignored by AgentOptions at startup,
            // allowing runtime metadata to disagree with the actually booted agent settings.
            if (component.indexOf('.') >= 0)
                throw new IllegalArgumentException("Configuration property names must not contain '.': " + component);
            String path = prefix.isEmpty() ? component : prefix + "." + component;
            if (value instanceof Map<?, ?>) flatten(path, (Map<?, ?>) value, target);
            else if (value instanceof Iterable<?> && (path.endsWith(".includes") || path.endsWith(".excludes"))) {
                StringBuilder joined = new StringBuilder();
                for (Object item : (Iterable<?>) value) {
                    if (!(item instanceof String)) throw new IllegalArgumentException("Method filter entries must be strings: " + path);
                    if (joined.length() > 0) joined.append(';');
                    joined.append((String)item);
                }
                target.put(path, joined.toString());
            } else target.put(path, value);
        });
    }
}
