package com.madlava.probes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeBridgeAccountingTest {
    @BeforeEach void reset() { ProbeBridge.resetForTests(); }

    @Test
    void chainedThrowableConstructorIsOneCreation() {
        ObservedThrowable value = new ObservedThrowable();
        String type = value.getClass().getName();

        // Simulate two instrumented constructors in a this(...) constructor chain completing
        // for the same final object. The bridge must de-duplicate both object counters.
        ProbeBridge.constructorInitialized(value, type);
        ProbeBridge.constructorComplete(value);
        ProbeBridge.constructorInitialized(value, type);
        ProbeBridge.constructorComplete(value);

        ProbeBridge.Snapshot snapshot = ProbeBridge.snapshot();
        assertEquals(1L, snapshot.constructed().get(type).longValue());
        assertEquals(1L, snapshot.throwableCreated().get(type).longValue());
    }

    private static final class ObservedThrowable extends RuntimeException { }
}
