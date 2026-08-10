package com.madlava.spark;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SparkObservationRegistry {
    private static final int MAX_ENTITIES=1024,MAX_EVENTS=256,MAX_FAILURES=128;
    private static final ConcurrentHashMap<String,WeakReference<Object>> ENTITIES=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,LongAdder> COUNTERS=new ConcurrentHashMap<>();
    private static final LongAdder DROPPED_ENTITIES=new LongAdder(),DROPPED_EVENT_KEYS=new LongAdder();
    private static final ArrayDeque<Map<String,String>> FAILURES=new ArrayDeque<>();
    private SparkObservationRegistry(){}

    public static void observe(String anonymousId,Object entity,String event){
        if(anonymousId==null||entity==null||event==null)return;
        registerEntity(anonymousId,entity);
        counterFor(event).increment();
    }

    private static void registerEntity(String id,Object entity){
        if(ENTITIES.containsKey(id)){ENTITIES.put(id,new WeakReference<>(entity));return;}
        synchronized(ENTITIES){
            if(ENTITIES.containsKey(id)){ENTITIES.put(id,new WeakReference<>(entity));return;}
            // Reclaim cleared weak entries before dropping a new live identity.
            ENTITIES.entrySet().removeIf(entry->entry.getValue().get()==null);
            if(ENTITIES.size()>=MAX_ENTITIES){DROPPED_ENTITIES.increment();return;}
            ENTITIES.put(id,new WeakReference<>(entity));
        }
    }

    private static LongAdder counterFor(String event){
        LongAdder existing=COUNTERS.get(event);if(existing!=null)return existing;
        synchronized(COUNTERS){
            existing=COUNTERS.get(event);if(existing!=null)return existing;
            if(COUNTERS.size()>=MAX_EVENTS-1){DROPPED_EVENT_KEYS.increment();return COUNTERS.computeIfAbsent("other",ignored->new LongAdder());}
            LongAdder created=new LongAdder();COUNTERS.put(event,created);return created;
        }
    }

    public static synchronized void failure(String phase,String category,String context){if(FAILURES.size()==MAX_FAILURES)FAILURES.removeFirst();Map<String,String> value=new LinkedHashMap<>();value.put("phase",safe(phase));value.put("category",safe(category));value.put("context",safe(context));FAILURES.addLast(value);}
    public static synchronized Map<String,Object> snapshot(ClassLoader loader){ENTITIES.entrySet().removeIf(entry->entry.getValue().get()==null);Map<String,Long> counters=new LinkedHashMap<>();COUNTERS.forEach((key,value)->counters.put(key,value.sum()));Map<String,Object> result=new LinkedHashMap<>();result.put("compatibility",SparkCompatibility.probe(loader));result.put("liveWeakEntities",ENTITIES.size());result.put("droppedEntityRegistrations",DROPPED_ENTITIES.sum());result.put("events",counters);result.put("droppedEventKeys",DROPPED_EVENT_KEYS.sum());result.put("failures",new ArrayDeque<>(FAILURES));result.put("forcedSparkActions",0);result.put("dataCapture",false);return result;}
    static synchronized void resetForTests(){ENTITIES.clear();COUNTERS.clear();FAILURES.clear();DROPPED_ENTITIES.reset();DROPPED_EVENT_KEYS.reset();}
    private static String safe(String value){if(value==null)return "unknown";return value.length()>80?value.substring(0,80):value;}
}
