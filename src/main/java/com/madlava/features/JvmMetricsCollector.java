package com.madlava.features;

import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class JvmMetricsCollector {
    public Map<String,Object> collect(){ return collect(ignored -> true); }

    /** Collect only enabled low-cost JVM features so configuration affects overhead as well as output. */
    public Map<String,Object> collect(Predicate<String> enabled){
        Predicate<String> selected = enabled == null ? ignored -> true : enabled;
        Map<String,Object> features=new LinkedHashMap<>();
        if(selected.test("heapUsage"))features.put("heapUsage",memory(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        if(selected.test("nonHeapUsage"))features.put("nonHeapUsage",memory(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()));
        if(selected.test("bufferPools"))features.put("bufferPools",buffers());
        if(selected.test("garbageCollection"))features.put("garbageCollection",gc());
        if(selected.test("threadStatistics"))features.put("threadStatistics",threads());
        if(selected.test("threadCpu"))features.put("threadCpu",threadCpu());
        if(selected.test("processResources"))features.put("processResources",process());
        if(selected.test("classLoaderInsights"))features.put("classLoaderInsights",classLoading());
        if(selected.test("jvmExecutionEngine"))features.put("jvmExecutionEngine",compilation());
        if(selected.test("selfObservability"))features.put("selfObservability",Map.of("source","madlava","availability","available","accuracy","exact"));
        return features;
    }
    private static Map<String,Object> memory(MemoryUsage u){return Map.of("usedBytes",nonnegative(u.getUsed()),"committedBytes",nonnegative(u.getCommitted()),"maximumBytes",u.getMax()<0?"unavailable":u.getMax(),"source","MemoryMXBean","accuracy","exact");}
    private static Map<String,Object> buffers(){return buffers(ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class));}
    static Map<String,Object> buffers(java.util.List<BufferPoolMXBean> pools){
        MetricSum count=new MetricSum(),used=new MetricSum(),capacity=new MetricSum();
        for(BufferPoolMXBean b:pools){count.add(b.getCount());used.add(b.getMemoryUsed());capacity.add(b.getTotalCapacity());}
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("count",count.value());result.put("memoryUsedBytes",used.value());result.put("totalCapacityBytes",capacity.value());
        result.put("unavailableCountMetrics",count.unavailable);result.put("unavailableMemoryMetrics",used.unavailable);result.put("unavailableCapacityMetrics",capacity.unavailable);
        result.put("source","BufferPoolMXBean");result.put("accuracy",accuracy(count,used,capacity));return result;
    }
    private static Map<String,Object> gc(){return gc(ManagementFactory.getGarbageCollectorMXBeans());}
    static Map<String,Object> gc(java.util.List<GarbageCollectorMXBean> collectors){
        MetricSum count=new MetricSum(),time=new MetricSum();
        for(GarbageCollectorMXBean b:collectors){count.add(b.getCollectionCount());time.add(b.getCollectionTime());}
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("collections",count.value());result.put("collectionTimeMillis",time.value());
        result.put("unavailableCollectionCountMetrics",count.unavailable);result.put("unavailableCollectionTimeMetrics",time.unavailable);
        result.put("source","GarbageCollectorMXBean");result.put("accuracy",accuracy(count,time));return result;
    }
    private static Map<String,Object> threads(){ThreadMXBean b=ManagementFactory.getThreadMXBean();return Map.of("live",b.getThreadCount(),"daemon",b.getDaemonThreadCount(),"peak",b.getPeakThreadCount(),"totalStarted",b.getTotalStartedThreadCount(),"source","ThreadMXBean","accuracy","exact");}
    private static Map<String,Object> threadCpu(){ThreadMXBean b=ManagementFactory.getThreadMXBean();return Map.of("available",b.isThreadCpuTimeSupported(),"enabled",b.isThreadCpuTimeSupported()&&b.isThreadCpuTimeEnabled(),"source","ThreadMXBean","accuracy",b.isThreadCpuTimeSupported()?"exact":"unavailable");}
    private static Map<String,Object> process(){
        OperatingSystemMXBean b=ManagementFactory.getOperatingSystemMXBean();double load=b.getSystemLoadAverage();
        Map<String,Object> result=new LinkedHashMap<>();result.put("availableProcessors",b.getAvailableProcessors());
        result.put("systemLoadAverage",Double.isFinite(load)&&load>=0d?load:"unavailable");
        result.put("systemLoadAverageAvailable",Double.isFinite(load)&&load>=0d);
        result.put("source","OperatingSystemMXBean");result.put("accuracy","jvm-exposed");return result;
    }
    private static Map<String,Object> classLoading(){ClassLoadingMXBean b=ManagementFactory.getClassLoadingMXBean();return Map.of("loaded",b.getLoadedClassCount(),"totalLoaded",b.getTotalLoadedClassCount(),"unloaded",b.getUnloadedClassCount(),"source","ClassLoadingMXBean","accuracy","exact");}
    private static Map<String,Object> compilation(){CompilationMXBean b=ManagementFactory.getCompilationMXBean();if(b==null)return Map.of("availability","unavailable","reason","CompilationMXBean absent");boolean supported=b.isCompilationTimeMonitoringSupported();Map<String,Object> result=new LinkedHashMap<>();result.put("compiler",b.getName());result.put("timeMonitoring",supported);result.put("totalCompilationTimeMillis",supported?b.getTotalCompilationTime():"unavailable");result.put("source","CompilationMXBean");result.put("accuracy",supported?"exact":"unavailable");return result;}
    private static long nonnegative(long value){return Math.max(0,value);}
    private static String accuracy(MetricSum... metrics){
        boolean anyAvailable=false,anyUnavailable=false;
        for(MetricSum metric:metrics){anyAvailable|=metric.available>0;anyUnavailable|=metric.unavailable>0;}
        if(!anyAvailable)return "unavailable";return anyUnavailable?"partial":"exact";
    }
    private static final class MetricSum{
        long total;int available,unavailable;
        void add(long value){if(value<0){unavailable++;return;}total+=value;available++;}
        Object value(){return available==0?"unavailable":total;}
    }
}
