package com.madlava.api;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MadLavaStatisticsApiTest {
    @Test void unavailableApiDoesNotInitializeRuntime() {
        MadLavaRuntimeRegistry.clear(null);
        assertFalse(MadLavaStatistics.isAvailable());
        assertTrue(MadLavaStatistics.snapshotJson().contains("AGENT_UNAVAILABLE"));
        assertFalse(MadLavaStatistics.releaseCheckpoint("does-not-exist"));
    }

    @Test void unknownCheckpointIsNotRenderedAsMetadataOnlyReport() {
        MadLavaRuntimeRegistry.clear(null);
        String report = MadLavaReport.reportSinceText("does-not-exist");
        assertTrue(report.contains("ERROR: UNKNOWN_CHECKPOINT"));
        assertFalse(report.contains("Method Profiling"));
    }
    @Test void deltaSubtractsNestedArgumentGroupsAndKeepsOnlyIntervalActivity() {
        Map<String,Object> baseline = methodSection(
                methodRow(10, 100, List.of(argumentGroup("a", 6), argumentGroup("b", 4))), 2);
        Map<String,Object> current = methodSection(
                methodRow(15, 190, List.of(argumentGroup("a", 8), argumentGroup("b", 4), argumentGroup("c", 3))), 5);

        Map<String,Object> delta = MadLavaStatistics.deltaSection(baseline, current);
        @SuppressWarnings("unchecked")
        Map<String,Object> method = (Map<String,Object>) ((List<?>) delta.get("methods")).get(0);

        assertEquals(5L, number(method.get("invocations")));
        assertEquals(5L, number(method.get("timedCompletions")));
        assertEquals(90L, number(method.get("totalDurationNanos")));
        assertEquals(18L, number(method.get("averageDurationNanos")));
        assertNull(method.get("minimumDurationNanos"));
        assertNull(method.get("maximumDurationNanos"));
        assertEquals(3L, number(delta.get("suppressedReentrantCallbacks")));

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> groups = (List<Map<String,Object>>) method.get("argumentGroups");
        assertEquals(2, groups.size());
        assertEquals(5L, groups.stream().mapToLong(group -> number(group.get("invocations"))).sum());
        assertEquals(2L, groupCount(groups, "a"));
        assertEquals(3L, groupCount(groups, "c"));
        assertEquals(0L, groupCount(groups, "b"));
    }

    @Test void deltaRecomputesSerializationAverageInsteadOfSubtractingDerivedDurations() {
        Map<String,Object> baselineGroup = serializationRow(4, 40);
        Map<String,Object> currentGroup = serializationRow(6, 70);
        Map<String,Object> baseline = new LinkedHashMap<>();
        baseline.put("droppedGroups", 1L);
        baseline.put("groups", List.of(baselineGroup));
        Map<String,Object> current = new LinkedHashMap<>();
        current.put("droppedGroups", 3L);
        current.put("groups", List.of(currentGroup));

        Map<String,Object> delta = MadLavaStatistics.deltaSection(baseline, current);
        @SuppressWarnings("unchecked")
        Map<String,Object> group = (Map<String,Object>) ((List<?>) delta.get("groups")).get(0);

        assertEquals(2L, number(group.get("operations")));
        assertEquals(30L, number(group.get("totalDurationNanos")));
        assertEquals(15L, number(group.get("averageDurationNanos")));
        assertNull(group.get("minimumDurationNanos"));
        assertNull(group.get("maximumDurationNanos"));
        assertEquals(2L, number(delta.get("droppedGroups")));
    }

    @Test void methodDeltaIdentityIncludesClassLoaderScope() {
        Map<String,Object> oldA=methodRow(10,100,List.of());oldA.put("loaderScope","loader-A");
        Map<String,Object> oldB=methodRow(20,200,List.of());oldB.put("loaderScope","loader-B");
        Map<String,Object> nowA=methodRow(13,130,List.of());nowA.put("loaderScope","loader-A");
        Map<String,Object> nowB=methodRow(27,270,List.of());nowB.put("loaderScope","loader-B");
        Map<String,Object> baseline=new LinkedHashMap<>();baseline.put("methods",List.of(oldA,oldB));
        Map<String,Object> current=new LinkedHashMap<>();current.put("methods",List.of(nowA,nowB));
        Map<String,Object> delta=MadLavaStatistics.deltaSection(baseline,current);
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)delta.get("methods");
        assertEquals(2,rows.size());
        assertEquals(3L,rows.stream().filter(r->"loader-A".equals(r.get("loaderScope"))).mapToLong(r->number(r.get("invocations"))).findFirst().orElseThrow());
        assertEquals(7L,rows.stream().filter(r->"loader-B".equals(r.get("loaderScope"))).mapToLong(r->number(r.get("invocations"))).findFirst().orElseThrow());
    }

    private static Map<String,Object> methodSection(Map<String,Object> method, long suppressed) {
        Map<String,Object> section = new LinkedHashMap<>();
        section.put("suppressedReentrantCallbacks", suppressed);
        section.put("methods", List.of(method));
        return section;
    }

    private static Map<String,Object> methodRow(long invocations, long totalNanos, List<Map<String,Object>> groups) {
        Map<String,Object> method = new LinkedHashMap<>();
        method.put("owner", "test/Example");
        method.put("method", "clean");
        method.put("descriptor", "()V");
        method.put("invocations", invocations);
        method.put("normalCompletions", invocations);
        method.put("exceptionalCompletions", 0L);
        method.put("timedCompletions", invocations);
        method.put("totalDurationNanos", totalNanos);
        method.put("minimumDurationNanos", 1L);
        method.put("maximumDurationNanos", 50L);
        method.put("averageDurationNanos", invocations == 0 ? 0L : totalNanos / invocations);
        method.put("droppedArgumentGroups", 0L);
        method.put("overflowArgumentInvocations", 0L);
        method.put("argumentGroups", groups);
        return method;
    }

    private static Map<String,Object> argumentGroup(String value, long invocations) {
        Map<String,Object> group = new LinkedHashMap<>();
        group.put("arguments", List.of(value));
        group.put("invocations", invocations);
        return group;
    }

    private static Map<String,Object> serializationRow(long operations, long totalNanos) {
        Map<String,Object> group = new LinkedHashMap<>();
        group.put("implementation", "serializer");
        group.put("operation", "SERIALIZE");
        group.put("layer", "BOUNDARY");
        group.put("rootClass", "root");
        group.put("byteAccuracy", "UNAVAILABLE");
        group.put("operations", operations);
        group.put("successfulOperations", operations);
        group.put("failedOperations", 0L);
        group.put("totalDurationNanos", totalNanos);
        group.put("minimumDurationNanos", 1L);
        group.put("maximumDurationNanos", 50L);
        group.put("averageDurationNanos", operations == 0 ? 0L : totalNanos / operations);
        group.put("operationsWithObservedBytes", 0L);
        group.put("observedBytes", 0L);
        group.put("nestedOperationsSuppressed", 0L);
        return group;
    }

    private static long groupCount(List<Map<String,Object>> groups, String value) {
        return groups.stream()
                .filter(group -> Objects.equals(group.get("arguments"), List.of(value)))
                .mapToLong(group -> number(group.get("invocations")))
                .findFirst().orElse(0L);
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }


    @Test void nullCheckpointIdsAndUnavailableHumanReportAreFailOpen() {
        MadLavaRuntimeRegistry.clear(null);
        assertTrue(MadLavaStatistics.snapshotSinceJson(null).contains("UNKNOWN_CHECKPOINT"));
        assertFalse(MadLavaStatistics.releaseCheckpoint(null));
        assertTrue(MadLavaReport.reportText().contains("ERROR: AGENT_UNAVAILABLE"));
        String markdown=MadLavaReport.reportSinceMarkdown("```");
        assertTrue(markdown.startsWith("````text\n"));
    }
}
