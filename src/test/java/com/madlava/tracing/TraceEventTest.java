package com.madlava.tracing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceEventTest {
    @Test void methodCallUsesStableMixedStreamEnvelope() {
        Map<String,Object> event = TraceEvent.methodCall(7L, "example.Target", "work", "()V", 42L, List.of("arg"));
        assertEquals(5, event.get("schemaVersion"));
        assertEquals("method-trace", event.get("recordType"));
        assertEquals("method-call", event.get("type"));
        assertEquals(7L, event.get("configurationVersion"));
        assertTrue(((Number) event.get("durationNanos")).longValue() >= 0L);
    }
}
