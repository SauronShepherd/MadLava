package com.madlava.serialization;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/** Serializer-specific callbacks injected into Spark's serializer implementation classes. */
public final class SparkSerializationBridge {
    private static final long DISABLED_TOKEN = 0L;
    private static final long ROOT_TOKEN = 1L;
    private static final long NESTED_TOKEN = 2L;

    private static final AtomicReference<Configuration> CONFIGURATION = new AtomicReference<>();
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static final ThreadLocal<Boolean> CALLBACK_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SparkSerializationBridge() {
    }

    public static void configure(
            SparkSerializationPlan plan,
            SparkSerializationMetrics metrics,
            boolean rootClassesEnabled) {
        CONFIGURATION.set(new Configuration(plan, metrics, rootClassesEnabled));
    }

    public static void clear() {
        CONFIGURATION.set(null);
    }

    /**
     * Starts a serializer observation. The primary argument is used only to derive
     * a bounded class name or an exact input ByteBuffer remaining count.
     */
    public static long enter(int targetId, Object primaryArgument) {
        Configuration configuration = CONFIGURATION.get();
        if (configuration == null || Boolean.TRUE.equals(CALLBACK_ACTIVE.get())) {
            return DISABLED_TOKEN;
        }
        CALLBACK_ACTIVE.set(Boolean.TRUE);
        try {
            SparkSerializationTarget target = configuration.plan.target(targetId);
            if (target == null) {
                return DISABLED_TOKEN;
            }
            State state = STATE.get();
            if (state.depth > 0) {
                state.depth++;
                if (state.frame != null) {
                    state.frame.nestedSuppressed++;
                }
                return NESTED_TOKEN;
            }

            state.depth = 1;
            long inputBytes = target.byteMode() == SparkSerializationTarget.ByteMode.INPUT_BYTE_BUFFER
                    ? remaining(primaryArgument)
                    : -1L;
            String rootClass = target.rootMode() == SparkSerializationTarget.RootMode.ENTRY_ARGUMENT
                    ? className(primaryArgument, configuration.rootClassesEnabled)
                    : target.rootMode() == SparkSerializationTarget.RootMode.NOT_APPLICABLE
                    ? "not-applicable"
                    : "pending-return-value";
            state.frame = new Frame(configuration, target, System.nanoTime(), inputBytes, rootClass);
            return ROOT_TOKEN;
        } catch (Throwable ignored) {
            safeBridgeFailure(configuration);
            safeReset();
            return DISABLED_TOKEN;
        } finally {
            CALLBACK_ACTIVE.remove();
        }
    }

    /** Normal completion callback. Argument order preserves an ARETURN value on the JVM stack. */
    public static void success(Object returnedValue, long token) {
        complete(returnedValue, token, true);
    }

    /** Exceptional completion callback. The Throwable is never retained or inspected. */
    public static void failure(Throwable ignoredFailure, long token) {
        complete(null, token, false);
    }

    private static void complete(Object returnedValue, long token, boolean success) {
        if (token == DISABLED_TOKEN || Boolean.TRUE.equals(CALLBACK_ACTIVE.get())) return;
        CALLBACK_ACTIVE.set(Boolean.TRUE);
        Configuration configuration = null;
        try {
            State state = STATE.get();
            if (state.depth <= 0) {
                safeReset();
                return;
            }
            state.depth--;
            if (token == NESTED_TOKEN) {
                if (state.depth == 0) {
                    safeReset();
                }
                return;
            }
            if (token != ROOT_TOKEN || state.frame == null) {
                safeReset();
                return;
            }

            Frame frame = state.frame;
            configuration = frame.configuration;
            if (configuration == null) {
                safeReset();
                return;
            }
            long duration = Math.max(0L, System.nanoTime() - frame.startedNanos);
            String rootClass = frame.rootClass;
            if (frame.target.rootMode() == SparkSerializationTarget.RootMode.RETURN_VALUE && success) {
                rootClass = className(returnedValue, configuration.rootClassesEnabled);
            }

            long bytes = -1L;
            ByteAccuracy accuracy = ByteAccuracy.UNAVAILABLE;
            if (success && frame.target.byteMode() == SparkSerializationTarget.ByteMode.RETURNED_BYTE_BUFFER) {
                bytes = remaining(returnedValue);
                if (bytes >= 0L) {
                    accuracy = ByteAccuracy.EXACT_RETURNED_BYTEBUFFER;
                }
            } else if (success && frame.target.byteMode() == SparkSerializationTarget.ByteMode.INPUT_BYTE_BUFFER) {
                bytes = frame.inputBytes;
                if (bytes >= 0L) {
                    accuracy = ByteAccuracy.EXACT_INPUT_BYTEBUFFER;
                }
            }

            configuration.metrics.record(
                    frame.target,
                    rootClass,
                    success,
                    duration,
                    bytes,
                    accuracy,
                    frame.nestedSuppressed);
            safeReset();
        } catch (Throwable ignored) {
            safeBridgeFailure(configuration);
            safeReset();
        } finally {
            CALLBACK_ACTIVE.remove();
        }
    }

    private static long remaining(Object candidate) {
        if (!(candidate instanceof ByteBuffer)) {
            return -1L;
        }
        try {
            return ((ByteBuffer) candidate).remaining();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String className(Object value, boolean enabled) {
        if (!enabled) {
            return "disabled";
        }
        if (value == null) {
            return "null";
        }
        try {
            String name = value.getClass().getName();
            return name.length() <= 256 ? name : name.substring(0, 256);
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static void safeBridgeFailure(Configuration configuration) {
        try { if (configuration != null) configuration.metrics.bridgeFailure(); }
        catch (Throwable ignored) { /* bridge diagnostics are fail-open too */ }
    }

    private static void safeReset() {
        try {
            STATE.remove();
        } catch (Throwable ignored) {
            // No application-visible failure.
        }
    }

    private static final class Configuration {
        private final SparkSerializationPlan plan;
        private final SparkSerializationMetrics metrics;
        private final boolean rootClassesEnabled;

        private Configuration(
                SparkSerializationPlan plan,
                SparkSerializationMetrics metrics,
                boolean rootClassesEnabled) {
            this.plan = plan;
            this.metrics = metrics;
            this.rootClassesEnabled = rootClassesEnabled;
        }
    }

    private static final class State {
        private int depth;
        private Frame frame;
    }

    private static final class Frame {
        private final Configuration configuration;
        private final SparkSerializationTarget target;
        private final long startedNanos;
        private final long inputBytes;
        private final String rootClass;
        private long nestedSuppressed;

        private Frame(
                Configuration configuration,
                SparkSerializationTarget target,
                long startedNanos,
                long inputBytes,
                String rootClass) {
            this.configuration = configuration;
            this.target = target;
            this.startedNanos = startedNanos;
            this.inputBytes = inputBytes;
            this.rootClass = rootClass;
        }
    }
}
