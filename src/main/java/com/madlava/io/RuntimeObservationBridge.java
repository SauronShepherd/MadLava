package com.madlava.io;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded, payload-free accounting shared by stream, network and serialization hooks. */
public final class RuntimeObservationBridge {
    private static final int MAX_GROUPS=2048;
    private static final ConcurrentHashMap<String,Counters> IO=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,Counters> SERIALIZATION=new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> SERIALIZATION_DEPTH=ThreadLocal.withInitial(()->0);
    private RuntimeObservationBridge(){}

    public static void io(String operation,String observedLayer,long actual,boolean success){
        if(excluded(observedLayer))return;
        String key=boundedKey(IO,operation+'|'+safeLayer(observedLayer));Counters counters=IO.computeIfAbsent(key,ignored->new Counters());
        counters.operations.increment();if(!success)counters.errors.increment();else if(actual<0)counters.eof.increment();else counters.bytes.add(actual);
    }

    public static boolean serializationEnter(){int depth=SERIALIZATION_DEPTH.get();SERIALIZATION_DEPTH.set(depth+1);return depth==0;}
    public static void serializationExit(boolean root,String implementation,long bytes,boolean success,String method,String accuracy){
        int depth=Math.max(0,SERIALIZATION_DEPTH.get()-1);if(depth==0)SERIALIZATION_DEPTH.remove();else SERIALIZATION_DEPTH.set(depth);
        if(!root)return;String key=boundedKey(SERIALIZATION,safeLayer(implementation)+'|'+safeLayer(method)+'|'+safeLayer(accuracy));Counters counters=SERIALIZATION.computeIfAbsent(key,ignored->new Counters());counters.operations.increment();if(success&&bytes>=0)counters.bytes.add(bytes);else if(!success)counters.errors.increment();
    }

    public static String anonymizeEndpoint(String endpoint){
        if(endpoint==null)return "unknown";try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));StringBuilder value=new StringBuilder("endpoint-");for(int i=0;i<8;i++)value.append(String.format("%02x",digest[i]));return value.toString();}catch(Exception ignored){return "endpoint-unavailable";}
    }

    public static Snapshot snapshot(){return new Snapshot(copy(IO),copy(SERIALIZATION));}
    static void resetForTests(){IO.clear();SERIALIZATION.clear();SERIALIZATION_DEPTH.remove();}
    private static boolean excluded(String layer){return Thread.currentThread().getName().startsWith("madlava-")||(layer!=null&&layer.startsWith("com.madlava."));}
    private static String safeLayer(String value){if(value==null||value.isBlank())return "unknown";return value.length()>160?value.substring(0,160):value;}
    private static String boundedKey(ConcurrentHashMap<String,Counters> map,String key){return map.containsKey(key)||map.size()<MAX_GROUPS?key:"other";}
    private static Map<String,Metric> copy(ConcurrentHashMap<String,Counters> source){Map<String,Metric> result=new LinkedHashMap<>();source.forEach((key,value)->result.put(key,new Metric(value.operations.sum(),value.bytes.sum(),value.eof.sum(),value.errors.sum())));return Collections.unmodifiableMap(result);}
    private static final class Counters{final LongAdder operations=new LongAdder(),bytes=new LongAdder(),eof=new LongAdder(),errors=new LongAdder();}
    public static final class Metric{public final long operations,bytes,eof,errors;private Metric(long operations,long bytes,long eof,long errors){this.operations=operations;this.bytes=bytes;this.eof=eof;this.errors=errors;}public Map<String,Object> report(){return Map.of("operations",operations,"bytes",bytes,"eof",eof,"errors",errors);}}
    public static final class Snapshot{private final Map<String,Metric> io,serialization;private Snapshot(Map<String,Metric> io,Map<String,Metric> serialization){this.io=io;this.serialization=serialization;}public Map<String,Metric> io(){return io;}public Map<String,Metric> serialization(){return serialization;}public Map<String,Object> ioReport(){return report(io);}public Map<String,Object> serializationReport(){return report(serialization);}private static Map<String,Object> report(Map<String,Metric> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((key,value)->result.put(key,value.report()));return result;}}
}
