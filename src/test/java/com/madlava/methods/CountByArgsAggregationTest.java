package com.madlava.methods;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CountByArgsAggregationTest {
    @Test void distinctInstancesWithSameCanonicalShapeShareOneGroup() {
        MethodRegistry registry = new MethodRegistry(8);
        MethodMetrics metrics = new MethodMetrics(registry);
        int id = registry.register(new MethodKey("test", "Example", "clean", "(Ljava/lang/Object;)V"));
        for (int i = 0; i < 5000; i++) { metrics.entered(id); metrics.normalCompletion(id, 1); metrics.traceArguments(id, 1, new Object[]{new HashMap<>()}); }
        Map<String,Object> report = metrics.report();
        Map<?,?> method = (Map<?,?>)((List<?>)report.get("methods")).get(0);
        assertEquals(1, ((List<?>)method.get("argumentGroups")).size());
        assertEquals(5000L, ((Number)((Map<?,?>)((List<?>)method.get("argumentGroups")).get(0)).get("invocations")).longValue());
        assertEquals(0L, ((Number)method.get("overflowArgumentInvocations")).longValue());
    }
    @Test void concurrentCallsShareOneCanonicalGroup() throws Exception {
        MethodRegistry registry = new MethodRegistry(8); MethodMetrics metrics = new MethodMetrics(registry);
        int id = registry.register(new MethodKey("test", "Example", "clean", "(Ljava/lang/Object;)V"));
        List<Thread> threads = new ArrayList<>();
        for (int t=0;t<8;t++) { Thread thread=new Thread(() -> { for(int i=0;i<1000;i++){metrics.entered(id);metrics.normalCompletion(id,1);metrics.traceArguments(id,1,new Object[]{new HashMap<>()});} }); threads.add(thread); thread.start(); }
        for(Thread thread:threads)thread.join();
        Map<?,?> method=(Map<?,?>)((List<?>)metrics.report().get("methods")).get(0);
        assertEquals(8000L,((Number)method.get("invocations")).longValue());
        assertEquals(8000L,((Number)((Map<?,?>)((List<?>)method.get("argumentGroups")).get(0)).get("invocations")).longValue());
    }
}
