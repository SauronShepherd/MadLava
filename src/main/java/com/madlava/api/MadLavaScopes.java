package com.madlava.api;

import com.madlava.reporting.AgentRuntime;
import com.madlava.reporting.Json;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** High-level named profiling intervals backed by MadLava checkpoints. */
public final class MadLavaScopes {
    private static final int MAX_ACTIVE = 64, MAX_RESULTS = 64;
    private static final AtomicLong IDS = new AtomicLong(), RESULT_IDS = new AtomicLong();
    private static final ConcurrentHashMap<String, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ScopeResult> RESULTS = new ConcurrentHashMap<>();
    private static final Deque<String> RESULT_ORDER = new ArrayDeque<>();
    private MadLavaScopes() { }

    public static boolean isAvailable() { return MadLavaStatistics.isAvailable(); }

    public static String beginScope(String name) {
        if (!isAvailable()) return error("AGENT_UNAVAILABLE");
        if (name == null || name.isBlank() || name.length() > 256) return error("INVALID_SCOPE_NAME");
        synchronized (ACTIVE) { if (ACTIVE.size() >= MAX_ACTIVE) return error("TOO_MANY_ACTIVE_SCOPES"); }
        String checkpoint = MadLavaStatistics.checkpoint();
        if (checkpoint.isEmpty()) return error("AGENT_UNAVAILABLE");
        AgentRuntime runtime = MadLavaRuntimeRegistry.current();
        Map<String,Object> start = runtime.snapshot("scope-start");
        String id = "scope-" + String.format("%016d", IDS.incrementAndGet());
        ACTIVE.put(id, new Active(id, name, checkpoint, Instant.now(), System.nanoTime(), number(start.get("configurationVersion"))));
        return id;
    }

    public static String endScope(String scopeId) {
        Active scope = ACTIVE.remove(scopeId);
        if (scope == null) return error("UNKNOWN_SCOPE_OR_ALREADY_ENDED");
        try {
            Map<String,Object> statistics = MadLavaStatistics.sinceMap(scope.checkpoint);
            MadLavaStatistics.releaseCheckpoint(scope.checkpoint);
            if ("ERROR".equals(statistics.get("status"))) return error("SCOPE_DELTA_UNAVAILABLE");
            AgentRuntime runtime = MadLavaRuntimeRegistry.current();
            Map<String,Object> end = runtime == null ? Map.of() : runtime.snapshot("scope-end");
            String resultId = "scope-result-" + String.format("%016d", RESULT_IDS.incrementAndGet());
            ScopeResult result = new ScopeResult(resultId, scope, Instant.now(), Math.max(0L, System.nanoTime() - scope.startedNanos),
                    number(end.get("configurationVersion")), statistics);
            synchronized (RESULT_ORDER) { while (RESULT_ORDER.size() >= MAX_RESULTS) RESULTS.remove(RESULT_ORDER.removeFirst()); RESULT_ORDER.addLast(resultId); }
            RESULTS.put(resultId, result);
            return resultId;
        } catch (RuntimeException ex) {
            MadLavaStatistics.releaseCheckpoint(scope.checkpoint);
            return error("SCOPE_END_FAILED");
        }
    }

    public static String endScopeReportText(String scopeId) { String result = endScope(scopeId); return result.startsWith("scope-result-") ? MadLavaReport.scopeReportText(result) : result; }
    public static String scopeResultJson(String resultId) { ScopeResult result = RESULTS.get(resultId); return result == null ? error("UNKNOWN_OR_EXPIRED_SCOPE_RESULT") : Json.encode(result.asMap()); }
    public static boolean releaseScopeResult(String resultId) { return RESULTS.remove(resultId) != null; }
    public static String activeScopesJson() { return Json.encode(ACTIVE.keySet()); }
    public static String scopeStatusJson(String scopeId) { Active a=ACTIVE.get(scopeId); return a==null?error("UNKNOWN_SCOPE"):Json.encode(Map.of("status","OK","scopeId",scopeId,"scopeName",a.name,"state","ACTIVE")); }

    static ScopeResult result(String id) { return RESULTS.get(id); }
    private static long number(Object v) { return v instanceof Number ? ((Number)v).longValue() : 0; }
    private static String error(String reason) { return Json.encode(Map.of("status","ERROR","reason",reason)); }

    private static final class Active {
        final String id, name, checkpoint; final Instant startedAt; final long startedNanos, configurationVersion;
        Active(String id,String name,String checkpoint,Instant startedAt,long startedNanos,long configurationVersion){this.id=id;this.name=name;this.checkpoint=checkpoint;this.startedAt=startedAt;this.startedNanos=startedNanos;this.configurationVersion=configurationVersion;}
    }
    static final class ScopeResult {
        final String resultId; final Active scope; final Instant endedAt; final long durationNanos; final long configurationVersionAtEnd; final Map<String,Object> statistics;
        ScopeResult(String resultId, Active scope, Instant endedAt, long durationNanos, long configurationVersionAtEnd, Map<String,Object> statistics) { this.resultId=resultId;this.scope=scope;this.endedAt=endedAt;this.durationNanos=durationNanos;this.configurationVersionAtEnd=configurationVersionAtEnd;this.statistics=deepCopy(statistics); }
        String scopeName(){return scope.name;}
        String scopeId(){return scope.id;}
        long durationNanos(){return durationNanos;}
        Map<String,Object> asMap(){Map<String,Object> out=new LinkedHashMap<>();out.put("status","OK");out.put("schemaVersion",1);out.put("resultId",resultId);out.put("scopeId",scope.id);out.put("scopeName",scope.name);out.put("startedAt",scope.startedAt.toString());out.put("endedAt",endedAt.toString());out.put("durationNanos",durationNanos);out.put("configurationVersionAtStart",scope.configurationVersion);out.put("configurationVersionAtEnd",configurationVersionAtEnd);out.put("configurationChanged",scope.configurationVersion!=configurationVersionAtEnd);out.put("statistics",statistics);return out;}
        @SuppressWarnings("unchecked") private static Map<String,Object> deepCopy(Map<String,Object> input){return (Map<String,Object>)copy(input);}
        private static Object copy(Object v){if(v instanceof Map){Map<?,?> m=(Map<?,?>)v;Map<String,Object> out=new LinkedHashMap<>();for(Map.Entry<?,?> e:m.entrySet())out.put(String.valueOf(e.getKey()),copy(e.getValue()));return out;}if(v instanceof List){List<?> l=(List<?>)v;List<Object> out=new ArrayList<>();for(Object x:l)out.add(copy(x));return out;}return v;}
    }
}
