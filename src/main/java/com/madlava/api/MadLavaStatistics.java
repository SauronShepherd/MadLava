package com.madlava.api;

import com.madlava.reporting.AgentRuntime;
import com.madlava.reporting.Json;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Py4J-friendly, read-only runtime statistics bridge. */
public final class MadLavaStatistics {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final int MAX_CHECKPOINTS = 64;
    private static final ConcurrentHashMap<String, Map<String,Object>> CHECKPOINTS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedDeque<String> CHECKPOINT_ORDER = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** Checkpoints owned by active scopes cannot be evicted by unrelated manual checkpoints. */
    private static final Set<String> PINNED_CHECKPOINTS = new HashSet<>();
    private MadLavaStatistics() { }
    public static boolean isAvailable() { return MadLavaRuntimeRegistry.current() != null; }
    public static String statusJson() { Map<String,Object> status=new LinkedHashMap<>();status.put("available",isAvailable());status.put("apiVersion",1);status.put("schemaVersion",5);return Json.encode(status); }
    public static String snapshotJson() { return currentJson("snapshot"); }
    public static String methodProfilingJson() { return sectionJson("methodProfiling"); }
    public static String sparkSerializationJson() { return sectionJson("sparkSerialization"); }
    public static synchronized String checkpoint() { return checkpoint(false); }
    static synchronized String pinnedCheckpoint() { return checkpoint(true); }
    private static String checkpoint(boolean pinned) {
        AgentRuntime runtime=MadLavaRuntimeRegistry.current();
        if(runtime==null)return "";
        while(CHECKPOINTS.size()>=MAX_CHECKPOINTS){
            String evictable=null;
            for(String candidate:CHECKPOINT_ORDER){if(!PINNED_CHECKPOINTS.contains(candidate)){evictable=candidate;break;}}
            if(evictable==null)return ""; // all capacity is reserved by active scopes
            CHECKPOINT_ORDER.remove(evictable);
            CHECKPOINTS.remove(evictable);
        }
        String id="cp-"+String.format("%016d",SEQUENCE.incrementAndGet());
        CHECKPOINTS.put(id,runtime.snapshot("checkpoint"));
        CHECKPOINT_ORDER.addLast(id);
        if(pinned)PINNED_CHECKPOINTS.add(id);
        return id;
    }
    public static String snapshotSinceJson(String checkpointId) { if(checkpointId==null||checkpointId.isBlank())return error("UNKNOWN_CHECKPOINT");Map<String,Object> baseline=CHECKPOINTS.get(checkpointId);if(baseline==null)return error("UNKNOWN_CHECKPOINT");AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Map<String,Object> current=runtime.snapshot("since-"+checkpointId);Map<String,Object> result=new LinkedHashMap<>();result.put("apiVersion",1);result.put("schemaVersion",5);result.put("checkpoint",checkpointId);result.put("checkpointCreatedAt",baseline.get("timestamp"));result.put("queriedAt",Instant.now().toString());result.put("checkpointConfigurationVersion",baseline.getOrDefault("configurationVersion",0));result.put("currentConfigurationVersion",current.getOrDefault("configurationVersion",0));result.put("configurationChanged",!Objects.equals(baseline.get("configurationVersion"),current.get("configurationVersion")));result.put("methodProfiling",deltaSection(section(baseline,"methodProfiling"),section(current,"methodProfiling")));result.put("sparkSerialization",deltaSection(section(baseline,"sparkSerialization"),section(current,"sparkSerialization")));return Json.encode(result); }
    public static String methodProfilingSinceJson(String checkpointId) { return sectionSince(checkpointId,"methodProfiling"); }
    public static String sparkSerializationSinceJson(String checkpointId) { return sectionSince(checkpointId,"sparkSerialization"); }
    static synchronized long checkpointConfigurationVersion(String checkpointId) {
        if(checkpointId==null||checkpointId.isBlank())return 0L;
        Map<String,Object> snapshot = CHECKPOINTS.get(checkpointId);
        return snapshot == null ? 0L : number(snapshot.get("configurationVersion"));
    }
    static synchronized Instant checkpointCreatedAt(String checkpointId) {
        if(checkpointId==null||checkpointId.isBlank())return Instant.now();
        Map<String,Object> snapshot = CHECKPOINTS.get(checkpointId);
        Object value = snapshot == null ? null : snapshot.get("timestamp");
        if (value != null) {
            try { return Instant.parse(String.valueOf(value)); } catch (RuntimeException ignored) { }
        }
        return Instant.now();
    }
    public static synchronized boolean releaseCheckpoint(String checkpointId) {
        if(checkpointId==null||checkpointId.isBlank())return false;
        boolean removed=CHECKPOINTS.remove(checkpointId)!=null;
        if(removed)CHECKPOINT_ORDER.remove(checkpointId);
        PINNED_CHECKPOINTS.remove(checkpointId);
        return removed;
    }
    static synchronized void resetForTests(){CHECKPOINTS.clear();CHECKPOINT_ORDER.clear();PINNED_CHECKPOINTS.clear();}
    static Map<String,Object> snapshotMap(){AgentRuntime runtime=MadLavaRuntimeRegistry.current();return runtime==null?new LinkedHashMap<>(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE")):runtime.snapshot("api");}
    static Map<String,Object> sinceMap(String id){
        if(id==null||id.isBlank())return new LinkedHashMap<>(Map.of("status","ERROR","reason","UNKNOWN_CHECKPOINT"));
        Map<String,Object> baseline=CHECKPOINTS.get(id); AgentRuntime runtime=MadLavaRuntimeRegistry.current();
        if(baseline==null)return new LinkedHashMap<>(Map.of("status","ERROR","reason","UNKNOWN_CHECKPOINT"));
        if(runtime==null)return new LinkedHashMap<>(Map.of("status","ERROR","reason","AGENT_UNAVAILABLE"));
        Map<String,Object> current=runtime.snapshot("api-since"); Map<String,Object> result=new LinkedHashMap<>();
        result.put("schemaVersion",current.getOrDefault("schemaVersion",1)); result.put("agentVersion",current.get("agentVersion"));
        result.put("configurationVersion",current.getOrDefault("configurationVersion",0)); result.put("pid",current.get("pid"));
        result.put("effectiveConfiguration", current.get("effectiveConfiguration"));
        Map<String,Object> features=new LinkedHashMap<>();
        features.put("methodProfiling",deltaSection(section(baseline,"methodProfiling"),section(current,"methodProfiling")));
        features.put("sparkSerialization",deltaSection(section(baseline,"sparkSerialization"),section(current,"sparkSerialization")));
        result.put("features",features); return result;
    }
    private static String currentJson(String reason){AgentRuntime runtime=MadLavaRuntimeRegistry.current();return runtime==null?error("AGENT_UNAVAILABLE"):Json.encode(runtime.snapshot(reason));}
    private static String sectionJson(String section){AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Object value=runtime.snapshot("api").get("features");return Json.encode(value instanceof Map<?,?>?((Map<?,?>)value).get(section):null);}
    private static String sectionSince(String id,String section){if(id==null||id.isBlank())return error("UNKNOWN_CHECKPOINT");Map<String,Object> baseline=CHECKPOINTS.get(id);if(baseline==null)return error("UNKNOWN_CHECKPOINT");AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Map<String,Object> current=runtime.snapshot("api");return Json.encode(deltaSection(section(baseline,section),section(current,section)));}
    private static Map<String,Object> section(Map<String,Object> snapshot,String name){Object features=snapshot.get("features");if(features instanceof Map<?,?>){Object value=((Map<?,?>)features).get(name);if(value instanceof Map<?,?>){Map<String,Object> copy=new LinkedHashMap<>();((Map<?,?>)value).forEach((k,v)->copy.put(String.valueOf(k),v));return copy;}}return new LinkedHashMap<>();}
    /** Package-private for focused delta regression tests. */
    static Map<String,Object> deltaSection(Map<String,Object> baseline,Map<String,Object> current) {
        Map<String,Object> result = new LinkedHashMap<>(current);

        // These are cumulative counters at section level. Capacity/current-size fields are gauges and stay current.
        deltaScalars(result, baseline, List.of(
                "droppedMethodRegistrations", "suppressedReentrantCallbacks",
                "droppedGroups", "suppressedNestedOperations", "bridgeFailures"));

        deltaList(result, baseline, "methods", List.of("loaderScope", "owner", "method", "descriptor"), List.of(
                "invocations", "normalCompletions", "exceptionalCompletions", "timedCompletions",
                "totalDurationNanos", "droppedArgumentGroups", "overflowArgumentInvocations"),
                "timedCompletions", true);
        deltaList(result, baseline, "groups", List.of("implementation", "operation", "layer", "rootClass", "byteAccuracy"), List.of(
                "operations", "successfulOperations", "failedOperations", "totalDurationNanos",
                "operationsWithObservedBytes", "observedBytes", "nestedOperationsSuppressed"),
                "operations", false);
        return result;
    }

    private static void deltaScalars(Map<String,Object> result, Map<String,Object> baseline, List<String> names) {
        for (String name : names) {
            Object current = result.get(name);
            if (current instanceof Number) {
                result.put(name, deltaNumber(current, baseline.get(name)));
            }
        }
    }

    private static void deltaList(
            Map<String,Object> result,
            Map<String,Object> baseline,
            String field,
            List<String> identity,
            List<String> additiveFields,
            String completionField,
            boolean methodRows) {
        Object values = result.get(field);
        if (!(values instanceof List<?>)) return;

        Map<List<Object>,Map<String,Object>> old = new HashMap<>();
        Object oldValues = baseline.get(field);
        if (oldValues instanceof List<?>) {
            for (Object item : (List<?>) oldValues) {
                if (item instanceof Map<?,?>) {
                    Map<String,Object> row = copy((Map<?,?>) item);
                    old.put(identityKey(row, identity), row);
                }
            }
        }

        List<Map<String,Object>> deltas = new ArrayList<>();
        for (Object item : (List<?>) values) {
            if (!(item instanceof Map<?,?>)) continue;
            Map<String,Object> now = copy((Map<?,?>) item);
            Map<String,Object> before = old.get(identityKey(now, identity));

            if (before != null) {
                for (String name : additiveFields) {
                    if (now.get(name) instanceof Number) {
                        now.put(name, deltaNumber(now.get(name), before.get(name)));
                    }
                }
            }

            if (methodRows) {
                deltaArgumentGroups(now, before);
            }
            recomputeAverageAndHandleExtrema(now, completionField, before != null);

            // A delta report should not repeat rows with no activity since the checkpoint.
            if (before == null || hasActivity(now, additiveFields) || hasArgumentActivity(now)) {
                deltas.add(now);
            }
        }
        result.put(field, deltas);
    }

    private static void deltaArgumentGroups(Map<String,Object> now, Map<String,Object> before) {
        Object currentGroups = now.get("argumentGroups");
        if (!(currentGroups instanceof List<?>)) return;

        Map<Object,Map<String,Object>> old = new HashMap<>();
        if (before != null && before.get("argumentGroups") instanceof List<?>) {
            for (Object item : (List<?>) before.get("argumentGroups")) {
                if (item instanceof Map<?,?>) {
                    Map<String,Object> group = copy((Map<?,?>) item);
                    old.put(group.get("arguments"), group);
                }
            }
        }

        List<Map<String,Object>> deltas = new ArrayList<>();
        for (Object item : (List<?>) currentGroups) {
            if (!(item instanceof Map<?,?>)) continue;
            Map<String,Object> group = copy((Map<?,?>) item);
            Map<String,Object> previous = old.get(group.get("arguments"));
            if (previous != null && group.get("invocations") instanceof Number) {
                group.put("invocations", deltaNumber(group.get("invocations"), previous.get("invocations")));
            }
            if (number(group.get("invocations")) > 0L) deltas.add(group);
        }
        if (deltas.isEmpty()) now.remove("argumentGroups");
        else now.put("argumentGroups", deltas);
    }

    private static void recomputeAverageAndHandleExtrema(Map<String,Object> row, String completionField, boolean existedAtCheckpoint) {
        if (!(row.get("totalDurationNanos") instanceof Number) || !(row.get(completionField) instanceof Number)) return;
        long completions = number(row.get(completionField));
        long total = number(row.get("totalDurationNanos"));
        row.put("averageDurationNanos", completions == 0L ? 0L : total / completions);

        if (completions == 0L) {
            row.put("minimumDurationNanos", 0L);
            row.put("maximumDurationNanos", 0L);
        } else if (existedAtCheckpoint) {
            // Cumulative min/max cannot be reconstructed for an interval from two aggregate snapshots.
            // Null is explicit and prevents the old, incorrect subtraction of extrema.
            row.put("minimumDurationNanos", null);
            row.put("maximumDurationNanos", null);
        }
    }

    private static boolean hasActivity(Map<String,Object> row, List<String> additiveFields) {
        for (String name : additiveFields) if (number(row.get(name)) > 0L) return true;
        return false;
    }

    private static boolean hasArgumentActivity(Map<String,Object> row) {
        Object groups = row.get("argumentGroups");
        return groups instanceof List<?> && !((List<?>) groups).isEmpty();
    }

    private static List<Object> identityKey(Map<String,Object> row, List<String> identity) {
        List<Object> key = new ArrayList<>(identity.size());
        for (String name : identity) key.add(row.get(name));
        return key;
    }

    private static long deltaNumber(Object current, Object baseline) {
        return Math.max(0L, number(current) - number(baseline));
    }
    private static Map<String,Object> copy(Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(String.valueOf(k),v));return result;}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
    private static String error(String reason){return Json.encode(Map.of("status","ERROR","reason",reason));}
}
