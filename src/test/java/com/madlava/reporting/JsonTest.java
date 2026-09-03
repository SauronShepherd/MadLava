package com.madlava.reporting;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void doesNotInvokeArbitraryToStringImplementations() {
        Explosive value = new Explosive();
        ExplosiveNumber number = new ExplosiveNumber();
        Map<Object, Object> input = new LinkedHashMap<>();
        input.put("object", value);
        input.put("number", number);
        input.put(value, List.of(value));

        String json = Json.encode(input);

        assertTrue(json.contains("<com.madlava.reporting.JsonTest$Explosive>"));
        assertTrue(json.contains("<com.madlava.reporting.JsonTest$ExplosiveNumber>"));
    }

    @Test
    void enumUsesNameRatherThanOverridableToString() {
        assertEquals("\"SAFE\"", Json.encode(ExplosiveEnum.SAFE));
    }

    @Test
    void nonFiniteFloatingPointValuesRemainValidJson() {
        assertEquals("null", Json.encode(Double.NaN));
        assertEquals("null", Json.encode(Float.POSITIVE_INFINITY));
    }

    private static final class Explosive {
        @Override public String toString() { throw new AssertionError("must not execute application toString"); }
    }

    private static final class ExplosiveNumber extends Number {
        @Override public int intValue() { return 1; }
        @Override public long longValue() { return 1; }
        @Override public float floatValue() { return 1; }
        @Override public double doubleValue() { return 1; }
        @Override public String toString() { throw new AssertionError("must not execute application Number.toString"); }
    }

    private enum ExplosiveEnum {
        SAFE;
        @Override public String toString() { throw new AssertionError("must not execute enum toString"); }
    }
}
