package com.madlava.spark;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparkCompatibilityTest {
    @Test
    void probeIsPassiveWhenSparkIsNotAvailable() {
        Map<String, Object> result = SparkCompatibility.probe(new ClassLoader(null) {
        });

        assertEquals("NOT_YET_OBSERVED", result.get("contextState"));
        assertEquals("UNAVAILABLE", result.get("state"));
    }
}
