package com.madlava.agent;

import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.RuntimeConfigurationManager;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MadLavaAgentTest {
    @Test void legacyNoOpSettingsAreReportedAsUnsupportedWhenNonDefault() {
        ConfigurationResolver resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(resolver, Map.of("security.token","secret"), "test");
        assertEquals("security.token", MadLavaAgent.unsupportedLegacyConfiguration(manager.current()));

        manager=new RuntimeConfigurationManager(resolver, Map.of("safety.featureSnapshotTimeoutMillis",2000), "test");
        assertEquals("safety.featureSnapshotTimeoutMillis", MadLavaAgent.unsupportedLegacyConfiguration(manager.current()));
    }

    @Test void methodRuleHotReloadRequiresRetransformationSupport() {
        assertTrue(MadLavaAgent.liveMethodRuleReloadSupported(true, true));
        assertFalse(MadLavaAgent.liveMethodRuleReloadSupported(true, false));
        assertFalse(MadLavaAgent.liveMethodRuleReloadSupported(false, true));
    }
    @Test void startupConfigurationCanonicalizationIsLengthFramed() {
        Map<String,String> first=new java.util.LinkedHashMap<>(); first.put("a","x\nb=y"); first.put("b","z");
        Map<String,String> second=new java.util.LinkedHashMap<>(); second.put("a","x"); second.put("b","y\nb=z");
        assertNotEquals(MadLavaAgent.canonical(first),MadLavaAgent.canonical(second));
    }

    @Test void successfulRetransformationUsesOneBatchSafepoint() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Class<?>[]> observed = new AtomicReference<>();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> {
                    assertEquals("retransformClasses", method.getName());
                    calls.incrementAndGet();
                    observed.set((Class<?>[]) arguments[0]);
                    return null;
                });

        int failures = MadLavaAgent.retransformCandidates(
                instrumentation, List.of(String.class, Integer.class, Long.class));

        assertEquals(0, failures);
        assertEquals(1, calls.get());
        assertArrayEquals(
                new Class<?>[]{String.class, Integer.class, Long.class}, observed.get());
    }

    @Test void failedBatchRetriesIndividuallyAndCountsOnlyFailedClasses() {
        AtomicInteger calls = new AtomicInteger();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, arguments) -> {
                    assertEquals("retransformClasses", method.getName());
                    calls.incrementAndGet();
                    Class<?>[] requested = (Class<?>[]) arguments[0];
                    if (requested.length > 1 || requested[0] == Integer.class) {
                        throw new UnmodifiableClassException(requested[0].getName());
                    }
                    return null;
                });

        int failures = MadLavaAgent.retransformCandidates(
                instrumentation, List.of(String.class, Integer.class, Long.class));

        assertEquals(1, failures);
        assertEquals(4, calls.get());
    }
}
