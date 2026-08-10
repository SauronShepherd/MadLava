package com.madlava.methods;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
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

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
