package com.madlava.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MadLavaStatisticsApiTest {
    @Test void unavailableApiDoesNotInitializeRuntime() {
        MadLavaRuntimeRegistry.clear(null);
        assertFalse(MadLavaStatistics.isAvailable());
        assertTrue(MadLavaStatistics.snapshotJson().contains("AGENT_UNAVAILABLE"));
        assertFalse(MadLavaStatistics.releaseCheckpoint("does-not-exist"));
    }
}
