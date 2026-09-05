package com.madlava.methods;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodMetricsTracingTest {
    @Test
    void hotReloadPublishesTraceVersionSamplerAndSinkAsOneGeneration() throws Exception {
        MethodRegistry registry = new MethodRegistry(8);
        int methodId = registry.register(new MethodKey("test-loader", "example.Target", "call", "()V"));
        MethodMetrics metrics = new MethodMetrics(registry);
        AtomicLong mismatches = new AtomicLong();
        java.util.function.Consumer<java.util.Map<String,Object>> sinkOne = event -> {
            if (((Number) event.get("configurationVersion")).longValue() != 1L) mismatches.incrementAndGet();
        };
        java.util.function.Consumer<java.util.Map<String,Object>> sinkTwo = event -> {
            if (((Number) event.get("configurationVersion")).longValue() != 2L) mismatches.incrementAndGet();
        };
        metrics.enableTracing(1L, 1.0, sinkOne);

        CountDownLatch start = new CountDownLatch(1);
        Thread reloader = new Thread(() -> {
            await(start);
            for (int i = 0; i < 50_000; i++) {
                metrics.enableTracing(1L, 1.0, sinkOne);
                metrics.enableTracing(2L, 1.0, sinkTwo);
            }
        });
        Thread emitter = new Thread(() -> {
            await(start);
            for (int i = 0; i < 100_000; i++) metrics.normalCompletion(methodId, 1L);
        });
        reloader.start(); emitter.start(); start.countDown();
        reloader.join(); emitter.join();

        assertEquals(0L, mismatches.get());
    }

    @Test
    void liveSnapshotsNeverPublishUninitializedDurationExtrema() throws Exception {
        int methodCount = 2_000;
        MethodRegistry registry = new MethodRegistry(methodCount);
        MethodMetrics metrics = new MethodMetrics(registry);
        int[] methodIds = new int[methodCount];
        for (int i = 0; i < methodCount; i++) {
            methodIds[i] = registry.register(new MethodKey("test-loader", "example.Target" + i, "call", "()V"));
            metrics.entered(methodIds[i]);
        }

        AtomicBoolean completing = new AtomicBoolean(true);
        AtomicLong invalidExtrema = new AtomicLong();
        Thread reporter = new Thread(() -> {
            while (completing.get()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> methods = (List<Map<String, Object>>) metrics.report().get("methods");
                for (Map<String, Object> method : methods) {
                    long completions = ((Number) method.get("timedCompletions")).longValue();
                    long minimum = ((Number) method.get("minimumDurationNanos")).longValue();
                    long maximum = ((Number) method.get("maximumDurationNanos")).longValue();
                    if (completions > 0 && (minimum == Long.MAX_VALUE || maximum == Long.MIN_VALUE)) {
                        invalidExtrema.incrementAndGet();
                    }
                }
            }
        });
        reporter.start();
        for (int methodId : methodIds) {
            metrics.normalCompletion(methodId, 7L);
        }
        completing.set(false);
        reporter.join();

        assertEquals(0L, invalidExtrema.get());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) metrics.report().get("methods");
        for (Map<String, Object> method : methods) {
            assertEquals(7L, ((Number) method.get("minimumDurationNanos")).longValue());
            assertEquals(7L, ((Number) method.get("maximumDurationNanos")).longValue());
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
