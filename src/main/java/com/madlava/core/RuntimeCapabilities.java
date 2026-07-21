package com.madlava.core;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeCapabilities {
    private final Map<String,Boolean> values;
    private RuntimeCapabilities(Map<String,Boolean> values){this.values=Collections.unmodifiableMap(values);}
    public static RuntimeCapabilities detect(){Map<String,Boolean> v=new LinkedHashMap<>();v.put("threadCpuTime",ManagementFactory.getThreadMXBean().isThreadCpuTimeSupported());v.put("compilation",ManagementFactory.getCompilationMXBean()!=null);v.put("bufferPools",!ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class).isEmpty());v.put("unixOperatingSystem",ManagementFactory.getOperatingSystemMXBean().getClass().getName().contains("Unix"));return new RuntimeCapabilities(v);}
    public Map<String,Boolean> values(){return values;} public boolean available(String id){return values.getOrDefault(id,false);}
}
