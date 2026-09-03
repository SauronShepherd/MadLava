package com.madlava.diagnostics;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public final class DiagnosticsRuntime {
    private static volatile DiagnosticController controller;
    private DiagnosticsRuntime(){}
    public static synchronized void start(Path output)throws Exception{if(controller!=null)return;DiagnosticController candidate=new DiagnosticController(output.resolve("diagnostics"),8,1_048_576,8_388_608,4,Duration.ofMinutes(1),Duration.ofDays(1));MBeanServer server=ManagementFactory.getPlatformMBeanServer();ObjectName name=new ObjectName("com.madlava:type=DiagnosticController");if(!server.isRegistered(name))server.registerMBean(candidate,name);controller=candidate;}

    public static Map<String,Object> snapshot(){
        Map<String,Object> data=new LinkedHashMap<>();
        ThreadMXBean threads=ManagementFactory.getThreadMXBean();
        ThreadInfo[] info=threads.getThreadInfo(threads.getAllThreadIds(),0);
        int observedThreads=0;
        for(ThreadInfo item:info)if(item!=null)observedThreads++;
        data.put("executionSampling",Map.of(
                "source","THREAD_MXBEAN_SNAPSHOT","accuracy","SAMPLED",
                "threadCount",observedThreads,"requestedThreadCount",info.length));
        data.put("allocationProfiling",Map.of("state","UNAVAILABLE","source","JFR_NOT_ENABLED","accuracy","UNAVAILABLE"));
        data.put("contentionProfiling",Map.of("state",threads.isThreadContentionMonitoringSupported()?"AVAILABLE_DISABLED":"UNAVAILABLE","source","THREAD_MXBEAN","accuracy","SOURCE_SPECIFIC"));

        MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String,Object> heapData=new LinkedHashMap<>();
        heapData.put("usedBytes",availableLong(heap.getUsed()));
        heapData.put("source","MEMORY_MXBEAN");
        heapData.put("retainedSizeAvailable",false);
        heapData.put("accuracy",heap.getUsed()>=0?"EXACT":"UNAVAILABLE");
        data.put("heapDiagnostics",heapData);

        Long direct=null,mapped=null;
        int unavailablePools=0;
        for(BufferPoolMXBean pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){
            long value=pool.getMemoryUsed();
            if("direct".equals(pool.getName())){if(value>=0)direct=value;else unavailablePools++;}
            if("mapped".equals(pool.getName())){if(value>=0)mapped=value;else unavailablePools++;}
        }
        Map<String,Object> offHeap=new LinkedHashMap<>();
        offHeap.put("directBytes",direct==null?"unavailable":direct);
        offHeap.put("mappedBytes",mapped==null?"unavailable":mapped);
        offHeap.put("source","BUFFER_POOL_MXBEAN");
        offHeap.put("classOwnershipAvailable",false);
        int available=(direct==null?0:1)+(mapped==null?0:1);
        offHeap.put("accuracy",available==0?"UNAVAILABLE":(available<2||unavailablePools>0?"PARTIAL":"EXACT"));
        data.put("offHeapDiagnostics",offHeap);

        DiagnosticController value=controller;
        if(value!=null)data.put("control",Map.of("transport","PLATFORM_MBEAN_LOCAL","state",value.getState(),"incidentsDropped",value.incidents().dropped));
        return data;
    }

    private static Object availableLong(long value){return value<0?"unavailable":value;}
}
