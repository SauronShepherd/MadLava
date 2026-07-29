package com.madlava.methods;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static callback surface injected into application bytecode.
 *
 * <p>Every public callback is fail-open and payload-free. Callback failures are
 * swallowed so the observed application's behaviour always wins.</p>
 */
public final class MethodProbeBridge {
    private static final AtomicReference<MethodMetrics> METRICS = new AtomicReference<>();
    private static final ThreadLocal<Boolean> CALLBACK_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MethodProbeBridge() {
    }

    public static void configure(MethodMetrics metrics) {
        METRICS.set(metrics);
    }

    public static void clear() {
        METRICS.set(null);
    }

    public static long enter(int methodId) {
        MethodMetrics metrics = METRICS.get();
        if (metrics == null || methodId == MethodRegistry.REJECTED_ID) {
            return 0L;
        }
        if (Boolean.TRUE.equals(CALLBACK_ACTIVE.get())) {
            metrics.suppressedReentrantCallback();
            return 0L;
        }
        CALLBACK_ACTIVE.set(Boolean.TRUE);
        try {
            metrics.entered(methodId);
            long started = System.nanoTime();
            return started == 0L ? 1L : started;
        } catch (Throwable ignored) {
            return 0L;
        } finally {
            CALLBACK_ACTIVE.remove();
        }
    }

    public static void normalExit(int methodId, long startedNanos) {
        complete(methodId, startedNanos, false);
    }

    public static void exceptionalExit(int methodId, long startedNanos, Throwable ignoredFailure) {
        complete(methodId, startedNanos, true);
    }

    private static void complete(int methodId, long startedNanos, boolean exceptional) {
        MethodMetrics metrics = METRICS.get();
        if (metrics == null || methodId == MethodRegistry.REJECTED_ID || startedNanos == 0L) {
            return;
        }
        if (Boolean.TRUE.equals(CALLBACK_ACTIVE.get())) {
            metrics.suppressedReentrantCallback();
            return;
        }
        CALLBACK_ACTIVE.set(Boolean.TRUE);
        try {
            long duration = Math.max(0L, System.nanoTime() - startedNanos);
            if (exceptional) {
                metrics.exceptionalCompletion(methodId, duration);
            } else {
                metrics.normalCompletion(methodId, duration);
            }
        } catch (Throwable ignored) {
            // Deliberately fail open.
        } finally {
            CALLBACK_ACTIVE.remove();
        }
    }
}
