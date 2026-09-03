package com.madlava.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleJsonParserTest {
    @Test void rejectsLeadingZeroNumbers() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{\"x\":01}"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{\"x\":-01}"));
    }

    @Test void rejectsNonFiniteNumericResults() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{\"x\":1e309}"));
    }

    @Test void rejectsNonJsonDigitsAndWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{\"x\":١}"));
        assertThrows(IllegalArgumentException.class, () -> SimpleJsonParser.parse("{\u2003\"x\":1}"));
    }

    @Test void acceptsValidZeroAndExponentForms() {
        Map<?,?> value = (Map<?,?>) SimpleJsonParser.parse("{\"zero\":0,\"negativeZero\":-0,\"fraction\":0.5,\"exponent\":1e3}");
        assertEquals(0L, value.get("zero"));
        assertEquals(0L, value.get("negativeZero"));
        assertEquals(0.5d, value.get("fraction"));
        assertEquals(1000.0d, value.get("exponent"));
    }

    @Test void rejectsExcessiveNestingBeforeRecursionCanExhaustTheThreadStack() {
        String json="[".repeat(65)+"0"+"]".repeat(65);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->SimpleJsonParser.parse(json));
        assertTrue(failure.getMessage().contains("nesting exceeds"));
    }
}
