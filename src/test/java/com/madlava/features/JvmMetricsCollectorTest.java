package com.madlava.features;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JvmMetricsCollectorTest {
    @Test
    void publishesAllIterationTwoLowCostFeatures() {
        Map<String,Object> data=new JvmMetricsCollector().collect();
        for(String id:new String[]{"heapUsage","nonHeapUsage","bufferPools","garbageCollection","threadStatistics","threadCpu","processResources","classLoaderInsights","jvmExecutionEngine","selfObservability"})
            assertTrue(data.containsKey(id),id);
        assertTrue(((Map<?,?>)data.get("heapUsage")).containsKey("accuracy"));
    }

    @Test
    void disabledFeaturesAreNotCollected() {
        Map<String,Object> data=new JvmMetricsCollector().collect(feature -> !"heapUsage".equals(feature) && !"garbageCollection".equals(feature));
        assertFalse(data.containsKey("heapUsage"));
        assertFalse(data.containsKey("garbageCollection"));
        assertTrue(data.containsKey("threadStatistics"));
    }

    @Test
    void unavailableMxBeanValuesAreNotFabricatedAsExactZeros() {
        java.lang.management.GarbageCollectorMXBean unavailableGc = proxy(java.lang.management.GarbageCollectorMXBean.class, java.util.Map.of(
                "getCollectionCount", -1L, "getCollectionTime", -1L, "getName", "unknown"));
        Map<String,Object> gc = JvmMetricsCollector.gc(java.util.List.of(unavailableGc));
        assertEquals("unavailable", gc.get("collections"));
        assertEquals("unavailable", gc.get("collectionTimeMillis"));
        assertEquals("unavailable", gc.get("accuracy"));

        java.lang.management.GarbageCollectorMXBean knownGc = proxy(java.lang.management.GarbageCollectorMXBean.class, java.util.Map.of(
                "getCollectionCount", 4L, "getCollectionTime", 12L, "getName", "known"));
        Map<String,Object> partial = JvmMetricsCollector.gc(java.util.List.of(knownGc, unavailableGc));
        assertEquals(4L, partial.get("collections"));
        assertEquals(12L, partial.get("collectionTimeMillis"));
        assertEquals("partial", partial.get("accuracy"));

        java.lang.management.BufferPoolMXBean unavailableBuffer = proxy(java.lang.management.BufferPoolMXBean.class, java.util.Map.of(
                "getCount", -1L, "getMemoryUsed", -1L, "getTotalCapacity", -1L, "getName", "unknown"));
        Map<String,Object> buffers = JvmMetricsCollector.buffers(java.util.List.of(unavailableBuffer));
        assertEquals("unavailable", buffers.get("count"));
        assertEquals("unavailable", buffers.get("memoryUsedBytes"));
        assertEquals("unavailable", buffers.get("totalCapacityBytes"));
        assertEquals("unavailable", buffers.get("accuracy"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.Map<String,Object> values) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (values.containsKey(method.getName())) return values.get(method.getName());
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == long.class) return 0L;
            return null;
        });
    }
}
