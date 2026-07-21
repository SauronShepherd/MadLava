package com.madlava.spark;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SparkObservationRegistry {
    private static final int MAX_ENTITIES=1024,MAX_FAILURES=128;private static final ConcurrentHashMap<String,WeakReference<Object>> ENTITIES=new ConcurrentHashMap<>();private static final ConcurrentHashMap<String,LongAdder> COUNTERS=new ConcurrentHashMap<>();private static final ArrayDeque<Map<String,String>> FAILURES=new ArrayDeque<>();
    private SparkObservationRegistry(){}
    public static void observe(String anonymousId,Object entity,String event){if(anonymousId==null||entity==null||event==null)return;if(ENTITIES.size()<MAX_ENTITIES||ENTITIES.containsKey(anonymousId))ENTITIES.put(anonymousId,new WeakReference<>(entity));COUNTERS.computeIfAbsent(event,ignored->new LongAdder()).increment();}
    public static synchronized void failure(String phase,String category,String context){if(FAILURES.size()==MAX_FAILURES)FAILURES.removeFirst();Map<String,String> value=new LinkedHashMap<>();value.put("phase",safe(phase));value.put("category",safe(category));value.put("context",safe(context));FAILURES.addLast(value);}
    public static synchronized Map<String,Object> snapshot(ClassLoader loader){ENTITIES.entrySet().removeIf(entry->entry.getValue().get()==null);Map<String,Long> counters=new LinkedHashMap<>();COUNTERS.forEach((key,value)->counters.put(key,value.sum()));Map<String,Object> result=new LinkedHashMap<>();result.put("compatibility",SparkCompatibility.probe(loader));result.put("liveWeakEntities",ENTITIES.size());result.put("events",counters);result.put("failures",new ArrayDeque<>(FAILURES));result.put("forcedSparkActions",0);result.put("dataCapture",false);return result;}
    private static String safe(String value){if(value==null)return "unknown";return value.length()>80?value.substring(0,80):value;}
}
