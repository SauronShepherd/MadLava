package com.madlava.features;
import static org.junit.jupiter.api.Assertions.*;import java.util.Map;import org.junit.jupiter.api.Test;
class JvmMetricsCollectorTest { @Test void publishesAllIterationTwoLowCostFeatures(){Map<String,Object> data=new JvmMetricsCollector().collect();for(String id:new String[]{"heapUsage","nonHeapUsage","bufferPools","garbageCollection","threadStatistics","threadCpu","processResources","classLoaderInsights","jvmExecutionEngine","selfObservability"})assertTrue(data.containsKey(id),id);assertTrue(((Map<?,?>)data.get("heapUsage")).containsKey("accuracy"));} }
