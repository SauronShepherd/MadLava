package com.madlava.tracing;

import java.time.Instant;
import java.util.*;

public final class TraceEvent {
    private TraceEvent() { }
    public static Map<String,Object> methodCall(long configurationVersion, String owner, String method,
                                                 String descriptor, long durationNanos, List<String> arguments) {
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("schemaVersion",5);
        event.put("recordType","method-trace");
        event.put("type","method-call");
        event.put("timestamp", Instant.now().toString()); event.put("configurationVersion",configurationVersion);
        event.put("thread",Map.of("id",Thread.currentThread().getId(),"name",Thread.currentThread().getName()));
        Map<String,Object> identity=new LinkedHashMap<>(); identity.put("owner",owner); identity.put("name",method); if(descriptor!=null)identity.put("descriptor",descriptor);
        event.put("method",identity); event.put("durationNanos",Math.max(0,durationNanos)); if(arguments!=null)event.put("arguments",List.copyOf(arguments));
        return Collections.unmodifiableMap(event);
    }
}
