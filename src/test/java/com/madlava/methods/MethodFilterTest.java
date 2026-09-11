package com.madlava.methods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodFilterTest {
    @Test void absentIncludesMatchNoClassesOrMethods() {
        MethodFilter filter = MethodFilter.parse(null, null);

        assertTrue(filter.includeSources().isEmpty());
        assertTrue(filter.excludeSources().isEmpty());
        assertFalse(filter.mayMatchClass("com.example.Work"));
        assertFalse(filter.matches("com.example.Work", "run", "()V"));
    }

    @Test void literalNullIsParsedAsConfigurationNotAnAbsenceSentinel() {
        assertThrows(IllegalArgumentException.class, () -> MethodFilter.parse("null", ""));
    }

    @Test void configuredIncludesStillMatchNormally() {
        MethodFilter filter = MethodFilter.parse("com.example.Work.run#()V", "");

        assertTrue(filter.mayMatchClass("com.example.Work"));
        assertTrue(filter.matches("com.example.Work", "run", "()V"));
        assertFalse(filter.matches("com.example.Work", "stop", "()V"));
    }
}
