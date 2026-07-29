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
    private MadLavaStatistics() { }
    public static boolean isAvailable() { return MadLavaRuntimeRegistry.current() != null; }
    public static String statusJson() { Map<String,Object> status=new LinkedHashMap<>();status.put("available",isAvailable());status.put("apiVersion",1);status.put("schemaVersion",5);return Json.encode(status); }
    public static String snapshotJson() { return currentJson("snapshot"); }
    public static String methodProfilingJson() { return sectionJson("methodProfiling"); }
    public static String sparkSerializationJson() { return sectionJson("sparkSerialization"); }
    public static synchronized String checkpoint() { AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return "";while(CHECKPOINTS.size()>=MAX_CHECKPOINTS){String oldest=CHECKPOINT_ORDER.pollFirst();if(oldest==null)break;CHECKPOINTS.remove(oldest);}String id="cp-"+String.format("%016d",SEQUENCE.incrementAndGet());CHECKPOINTS.put(id,runtime.snapshot("checkpoint"));CHECKPOINT_ORDER.addLast(id);return id; }
    public static String snapshotSinceJson(String checkpointId) { Map<String,Object> baseline=CHECKPOINTS.get(checkpointId);if(baseline==null)return error("UNKNOWN_CHECKPOINT");AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Map<String,Object> current=runtime.snapshot("since-"+checkpointId);Map<String,Object> result=new LinkedHashMap<>();result.put("apiVersion",1);result.put("schemaVersion",5);result.put("checkpoint",checkpointId);result.put("checkpointCreatedAt",baseline.get("timestamp"));result.put("queriedAt",Instant.now().toString());result.put("checkpointConfigurationVersion",baseline.getOrDefault("configurationVersion",0));result.put("currentConfigurationVersion",current.getOrDefault("configurationVersion",0));result.put("configurationChanged",!Objects.equals(baseline.get("configurationVersion"),current.get("configurationVersion")));result.put("methodProfiling",deltaSection(section(baseline,"methodProfiling"),section(current,"methodProfiling")));result.put("sparkSerialization",deltaSection(section(baseline,"sparkSerialization"),section(current,"sparkSerialization")));return Json.encode(result); }
    public static String methodProfilingSinceJson(String checkpointId) { return sectionSince(checkpointId,"methodProfiling"); }
    public static String sparkSerializationSinceJson(String checkpointId) { return sectionSince(checkpointId,"sparkSerialization"); }
    public static boolean releaseCheckpoint(String checkpointId) { boolean removed=CHECKPOINTS.remove(checkpointId)!=null;if(removed)CHECKPOINT_ORDER.remove(checkpointId);return removed; }
    private static String currentJson(String reason){AgentRuntime runtime=MadLavaRuntimeRegistry.current();return runtime==null?error("AGENT_UNAVAILABLE"):Json.encode(runtime.snapshot(reason));}
    private static String sectionJson(String section){AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Object value=runtime.snapshot("api").get("features");return Json.encode(value instanceof Map<?,?>?((Map<?,?>)value).get(section):null);}
    private static String sectionSince(String id,String section){Map<String,Object> baseline=CHECKPOINTS.get(id);if(baseline==null)return error("UNKNOWN_CHECKPOINT");AgentRuntime runtime=MadLavaRuntimeRegistry.current();if(runtime==null)return error("AGENT_UNAVAILABLE");Map<String,Object> current=runtime.snapshot("api");return Json.encode(deltaSection(section(baseline,section),section(current,section)));}
    private static Map<String,Object> section(Map<String,Object> snapshot,String name){Object features=snapshot.get("features");if(features instanceof Map<?,?>){Object value=((Map<?,?>)features).get(name);if(value instanceof Map<?,?>){Map<String,Object> copy=new LinkedHashMap<>();((Map<?,?>)value).forEach((k,v)->copy.put(String.valueOf(k),v));return copy;}}return new LinkedHashMap<>();}
    private static Map<String,Object> deltaSection(Map<String,Object> baseline,Map<String,Object> current){Map<String,Object> result=new LinkedHashMap<>(current);deltaList(result,baseline,"methods",List.of("owner","method","descriptor"));deltaList(result,baseline,"groups",List.of("implementation","operation","layer","rootClass","byteAccuracy"));return result;}
    private static void deltaList(Map<String,Object> result,Map<String,Object> baseline,String field,List<String> identity){Object values=result.get(field);if(!(values instanceof List<?>))return;Map<String,Map<String,Object>> old=new HashMap<>();Object oldValues=baseline.get(field);if(oldValues instanceof List<?>)for(Object item:(List<?>)oldValues)if(item instanceof Map<?,?>){Map<?,?> m=(Map<?,?>)item;old.put(identity.stream().map(k->String.valueOf(m.get(k))).collect(java.util.stream.Collectors.joining("|")),copy(m));}List<Map<String,Object>> deltas=new ArrayList<>();for(Object item:(List<?>)values)if(item instanceof Map<?,?>){Map<String,Object> now=copy((Map<?,?>)item);String key=identity.stream().map(k->String.valueOf(now.get(k))).collect(java.util.stream.Collectors.joining("|"));Map<String,Object> before=old.get(key);if(before!=null)for(String name:new ArrayList<>(now.keySet()))if(now.get(name) instanceof Number)now.put(name,Math.max(0,((Number)now.get(name)).longValue()-number(before.get(name))));deltas.add(now);}result.put(field,deltas);}
    private static Map<String,Object> copy(Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(String.valueOf(k),v));return result;}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
    private static String error(String reason){return Json.encode(Map.of("status","ERROR","reason",reason));}
}
