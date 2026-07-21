package com.madlava.diagnostics;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
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
    public static synchronized void start(Path output)throws Exception{if(controller!=null)return;controller=new DiagnosticController(output.resolve("diagnostics"),8,1_048_576,8_388_608,4,Duration.ofMinutes(1),Duration.ofDays(1));MBeanServer server=ManagementFactory.getPlatformMBeanServer();ObjectName name=new ObjectName("com.madlava:type=DiagnosticController");if(!server.isRegistered(name))server.registerMBean(controller,name);}
    public static Map<String,Object> snapshot(){Map<String,Object> data=new LinkedHashMap<>();ThreadMXBean threads=ManagementFactory.getThreadMXBean();ThreadInfo[] info=threads.getThreadInfo(threads.getAllThreadIds(),0);data.put("executionSampling",Map.of("source","THREAD_MXBEAN_SNAPSHOT","accuracy","SAMPLED","threadCount",info.length));data.put("allocationProfiling",Map.of("state","UNAVAILABLE","source","JFR_NOT_ENABLED","accuracy","UNAVAILABLE"));data.put("contentionProfiling",Map.of("state",threads.isThreadContentionMonitoringSupported()?"AVAILABLE_DISABLED":"UNAVAILABLE","source","THREAD_MXBEAN","accuracy","SOURCE_SPECIFIC"));data.put("heapDiagnostics",Map.of("usedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),"source","MEMORY_MXBEAN","retainedSizeAvailable",false));long direct=0,mapped=0;for(BufferPoolMXBean pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){if("direct".equals(pool.getName()))direct=pool.getMemoryUsed();if("mapped".equals(pool.getName()))mapped=pool.getMemoryUsed();}data.put("offHeapDiagnostics",Map.of("directBytes",direct,"mappedBytes",mapped,"source","BUFFER_POOL_MXBEAN","classOwnershipAvailable",false));DiagnosticController value=controller;if(value!=null)data.put("control",Map.of("transport","PLATFORM_MBEAN_LOCAL","state",value.getState(),"incidentsDropped",value.incidents().dropped));return data;}
}
