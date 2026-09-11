package com.madlava.methods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MethodMetricsArgumentGuardTest {
    @Test void detachedMetricsIgnoreArgumentTracingWithoutRegistryLookup() {
        MethodMetrics metrics = new MethodMetrics(null);
        assertDoesNotThrow(() -> metrics.traceArguments(1, 10L, new Object[]{"value"}));
    }

    @Test void rejectedOrMissingArgumentsReturnBeforeLookup() {
        MethodRegistry registry = new MethodRegistry(4);
        MethodMetrics metrics = new MethodMetrics(registry);
        assertDoesNotThrow(() -> metrics.traceArguments(MethodRegistry.REJECTED_ID, 10L, new Object[]{"value"}));
        assertDoesNotThrow(() -> metrics.traceArguments(1, 10L, null));
    }
}
