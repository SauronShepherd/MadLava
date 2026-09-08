package com.madlava.methods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodFilterTest {
    @Test void removedIncludeSentinelMatchesNoClassesOrMethods() {
        MethodFilter filter = MethodFilter.parse(String.valueOf((Object) null), String.valueOf((Object) null));

        assertTrue(filter.includeSources().isEmpty());
        assertTrue(filter.excludeSources().isEmpty());
        assertFalse(filter.mayMatchClass("null"));
        assertFalse(filter.mayMatchClass("com.example.Work"));
        assertFalse(filter.matches("com.example.Work", "run", "()V"));
    }

    @Test void configuredIncludesStillMatchNormally() {
        MethodFilter filter = MethodFilter.parse("com.example.Work.run#()V", "");

        assertTrue(filter.mayMatchClass("com.example.Work"));
        assertTrue(filter.matches("com.example.Work", "run", "()V"));
        assertFalse(filter.matches("com.example.Work", "stop", "()V"));
    }
}
