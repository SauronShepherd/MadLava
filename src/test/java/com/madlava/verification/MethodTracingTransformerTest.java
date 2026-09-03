package com.madlava.verification;

import com.madlava.instrumentation.MadLavaTransformer;
import com.madlava.methods.MethodFilter;
import com.madlava.methods.MethodMetrics;
import com.madlava.methods.MethodObservationPlan;
import com.madlava.methods.MethodProbeBridge;
import com.madlava.methods.MethodRegistry;
import com.madlava.serialization.SparkSerializationPlan;
import com.madlava.serialization.SparkSerializationProfile;
import com.madlava.verification.support.TransformingClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MethodTracingTransformerTest {
    @Test
    void tracesNormalExceptionalRecursiveSynchronizedAndAllReturnKinds() throws Exception {
        MethodRegistry registry = new MethodRegistry(128);
        MethodMetrics metrics = new MethodMetrics(registry);
        MethodProbeBridge.configure(metrics);
        MadLavaTransformer transformer = new MadLavaTransformer(
                true,
                MethodFilter.parse("fixtures.SampleTarget.*", ""),
                registry,
                false,
                new SparkSerializationPlan(SparkSerializationProfile.ALL));

        Object target = new TransformingClassLoader(
                transformer,
                List.of("fixtures.SampleTarget"),
                "fixtures.")
                .loadClass("fixtures.SampleTarget")
                .getConstructor()
                .newInstance();

        assertEquals(5, invoke(target, "add", new Class<?>[]{int.class, int.class}, 2, 3));
        assertEquals(8L, invoke(target, "widen", new Class<?>[]{long.class}, 4L));
        assertEquals(3.0f, (Float) invoke(target, "scale", new Class<?>[]{float.class}, 2.0f), 0.001f);
        assertEquals(4.0d, (Double) invoke(target, "ratio", new Class<?>[]{double.class}, 8.0d), 0.001d);
        assertEquals("echo:lava", invoke(target, "echo", new Class<?>[]{String.class}, "lava"));
        invoke(target, "touch", new Class<?>[0]);
        assertEquals(1, invoke(target, "touched", new Class<?>[0]));
        assertEquals(2, invoke(target, "catchesInternally", new Class<?>[]{boolean.class}, true));
        assertEquals(3, invoke(target, "recursive", new Class<?>[]{int.class}, 3));
        assertEquals(9, invoke(target, "synchronizedMethod", new Class<?>[]{int.class}, 8));

        RuntimeException original = new RuntimeException("identity-must-survive");
        InvocationTargetException observed = org.junit.jupiter.api.Assertions.assertThrows(
                InvocationTargetException.class,
                () -> invoke(target, "failWith", new Class<?>[]{RuntimeException.class}, original));
        assertSame(original, observed.getCause());

        Map<String, Object> caught = findMethod(metrics, "catchesInternally", "(Z)I");
        assertEquals(1L, number(caught, "normalCompletions"));
        assertEquals(0L, number(caught, "exceptionalCompletions"));

        Map<String, Object> failed = findMethod(metrics, "failWith", "(Ljava/lang/RuntimeException;)I");
        assertEquals(0L, number(failed, "normalCompletions"));
        assertEquals(1L, number(failed, "exceptionalCompletions"));

        Map<String, Object> recursive = findMethod(metrics, "recursive", "(I)I");
        assertEquals(4L, number(recursive, "invocations"));
        assertEquals(4L, number(recursive, "normalCompletions"));
        assertTrue(number(recursive, "totalDurationNanos") >= 0L);
    }

    @Test
    void descriptorFilterSelectsOnlyRequestedOverload() throws Exception {
        MethodRegistry registry = new MethodRegistry(16);
        MethodMetrics metrics = new MethodMetrics(registry);
        MethodProbeBridge.configure(metrics);
        MadLavaTransformer transformer = new MadLavaTransformer(
                true,
                MethodFilter.parse("fixtures.SampleTarget.overloaded#(I)I", ""),
                registry,
                false,
                new SparkSerializationPlan(SparkSerializationProfile.ALL));
        Object target = new TransformingClassLoader(
                transformer,
                List.of("fixtures.SampleTarget"),
                "fixtures.")
                .loadClass("fixtures.SampleTarget")
                .getConstructor()
                .newInstance();

        invoke(target, "overloaded", new Class<?>[]{int.class}, 1);
        invoke(target, "overloaded", new Class<?>[]{String.class}, "x");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) metrics.report().get("methods");
        assertEquals(1, methods.size());
        assertEquals("(I)I", methods.get(0).get("descriptor"));
    }



    @Test
    void countByArgsIsRecordedAtInvocationEntryEvenWhileMethodIsStillRunning() throws Exception {
        String include = "fixtures.SampleTarget.blockOn(*)";
        MethodRegistry registry = new MethodRegistry(16);
        MethodMetrics metrics = new MethodMetrics(registry);
        MethodProbeBridge.configure(metrics);
        MadLavaTransformer transformer = new MadLavaTransformer(
                true, MethodFilter.parse(include, ""), registry, false,
                new SparkSerializationPlan(SparkSerializationProfile.ALL),
                MethodObservationPlan.compile(List.of(include)));
        Object target = new TransformingClassLoader(
                transformer, List.of("fixtures.SampleTarget"), "fixtures.")
                .loadClass("fixtures.SampleTarget").getConstructor().newInstance();

        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { invoke(target, "blockOn", new Class<?>[]{java.util.concurrent.CountDownLatch.class, java.util.concurrent.CountDownLatch.class, String.class}, entered, release, "scope"); }
            catch (Throwable t) { failure.set(t); }
        });
        worker.start();
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        try {
            Map<String,Object> row = findMethod(metrics, "blockOn", "(Ljava/util/concurrent/CountDownLatch;Ljava/util/concurrent/CountDownLatch;Ljava/lang/String;)V");
            assertEquals(1L, number(row, "invocations"));
            assertEquals(0L, number(row, "timedCompletions"));
            @SuppressWarnings("unchecked") List<Map<String,Object>> groups = (List<Map<String,Object>>) row.get("argumentGroups");
            assertEquals(1L, groups.stream().mapToLong(group -> number(group, "invocations")).sum());
        } finally {
            release.countDown(); worker.join(2000L);
        }
        assertEquals(null, failure.get());
    }

    @Test
    void exactArgumentObservationRuleCannotOverrideExclusion() throws Exception {
        String include = "fixtures.SampleTarget.overloaded(*)#(I)I";
        String exclude = "fixtures.SampleTarget.overloaded#(I)I";
        MethodRegistry registry = new MethodRegistry(16);
        MethodMetrics metrics = new MethodMetrics(registry);
        MethodProbeBridge.configure(metrics);
        MadLavaTransformer transformer = new MadLavaTransformer(
                true,
                MethodFilter.parse(include, exclude),
                registry,
                false,
                new SparkSerializationPlan(SparkSerializationProfile.ALL),
                MethodObservationPlan.compile(List.of(include)));
        Object target = new TransformingClassLoader(
                transformer,
                List.of("fixtures.SampleTarget"),
                "fixtures.")
                .loadClass("fixtures.SampleTarget")
                .getConstructor()
                .newInstance();

        assertEquals(11, invoke(target, "overloaded", new Class<?>[]{int.class}, 1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) metrics.report().get("methods");
        assertTrue(methods.isEmpty(), "Excluded method was unexpectedly instrumented");
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... values)
            throws Exception {
        Method method = target.getClass().getMethod(name, parameterTypes);
        return method.invoke(target, values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findMethod(MethodMetrics metrics, String name, String descriptor) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) metrics.report().get("methods");
        return rows.stream()
                .filter(row -> name.equals(row.get("method")) && descriptor.equals(row.get("descriptor")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method metric: " + name + descriptor));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
