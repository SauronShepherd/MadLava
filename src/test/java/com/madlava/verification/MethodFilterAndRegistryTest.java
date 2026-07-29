package com.madlava.verification;

import com.madlava.methods.MethodFilter;
import com.madlava.methods.MethodKey;
import com.madlava.methods.MethodRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MethodFilterAndRegistryTest {
    @Test
    void emptyIncludesMatchNothingAndExcludesOverrideIncludes() {
        assertFalse(MethodFilter.parse("", "").matches("a.A", "run", "()V"));

        MethodFilter filter = MethodFilter.parse(
                "a.*.*;b.B.run#()V",
                "a.Secret.*;b.B.run#()V");
        assertTrue(filter.matches("a.Visible", "run", "()V"));
        assertFalse(filter.matches("a.Secret", "run", "()V"));
        assertFalse(filter.matches("b.B", "run", "()V"));
    }

    @Test
    void registryIsStrictlyBoundedAndReportsDrops() {
        MethodRegistry registry = new MethodRegistry(1);
        int first = registry.register(new MethodKey("loader", "a.A", "one", "()V"));
        int same = registry.register(new MethodKey("loader", "a.A", "one", "()V"));
        int rejected = registry.register(new MethodKey("loader", "a.A", "two", "()V"));

        assertTrue(first > 0);
        assertEquals(first, same);
        assertEquals(MethodRegistry.REJECTED_ID, rejected);
        assertEquals(1, registry.size());
        assertEquals(1L, registry.droppedRegistrations());
    }
}
