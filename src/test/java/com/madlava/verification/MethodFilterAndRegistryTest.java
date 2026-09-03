package com.madlava.verification;

import com.madlava.methods.MethodFilter;
import com.madlava.methods.MethodKey;
import com.madlava.methods.MethodRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @Test
    void countByArgsSuffixBeforeDescriptorStillFiltersByDescriptor() {
        MethodFilter filter = MethodFilter.parse("a.B.m(*)#(I)V", "");
        assertTrue(filter.matches("a.B", "m", "(I)V"));
        assertFalse(filter.matches("a.B", "m", "(Ljava/lang/String;)V"));
    }

    @Test
    void provisionalRegistrationRollsBackWithoutConsumingCapacity() {
        MethodRegistry registry = new MethodRegistry(1);
        MethodRegistry.Reservation reservation = registry.reserve(new MethodKey("loader", "a.A", "one", "()V"));
        assertTrue(reservation.id() > 0);
        registry.rollback(reservation);
        assertEquals(0, registry.size());
        assertTrue(registry.register(new MethodKey("loader", "a.A", "two", "()V")) > 0);
    }

    @Test
    void oneCommittedReservationSurvivesAnotherTransformRollback() {
        MethodRegistry registry = new MethodRegistry(1);
        MethodKey key = new MethodKey("loader", "a.A", "one", "()V");
        MethodRegistry.Reservation first = registry.reserve(key);
        MethodRegistry.Reservation second = registry.reserve(key);
        registry.commit(first);
        registry.rollback(second);
        assertEquals(1, registry.size());
        assertEquals(first.id(), registry.register(key));
    }

    @Test
    void semicolonInsideJvmObjectDescriptorIsNotTreatedAsRuleSeparator() {
        MethodFilter filter = MethodFilter.parse(
                "a.B.m#(Ljava/lang/String;)V;x.Y.n#([Ljava/lang/Object;)I", "");
        assertTrue(filter.matches("a.B", "m", "(Ljava/lang/String;)V"));
        assertTrue(filter.matches("x.Y", "n", "([Ljava/lang/Object;)I"));
        assertFalse(filter.matches("a.B", "m", "(I)V"));
    }

    @Test
    void exactDescriptorsAreValidatedForIncludesAndExcludes() {
        assertThrows(IllegalArgumentException.class,
                () -> MethodFilter.parse("a.B.m#(Ljava/lang/String)V", ""));
        assertThrows(IllegalArgumentException.class,
                () -> MethodFilter.parse("a.B.m", "a.B.m#(Ljava/lang/String)V"));
        assertThrows(IllegalArgumentException.class,
                () -> MethodFilter.parse("a.B.m#", ""));
        assertTrue(MethodFilter.parse("a.B.m#(Ljava/lang/String;)V", "")
                .matches("a.B", "m", "(Ljava/lang/String;)V"));
    }

}
