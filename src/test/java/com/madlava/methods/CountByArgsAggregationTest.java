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
    @Test void resetClearsArgumentAndDiagnosticCountersWithoutClearingTheRegistry() {
        MethodRegistry registry = new MethodRegistry(1);
        MethodMetrics metrics = new MethodMetrics(registry, 1);
        int id = registry.register(new MethodKey("test", "Example", "clean", "(Ljava/lang/Object;)V"));
        assertEquals(MethodRegistry.REJECTED_ID, registry.register(new MethodKey("test", "Other", "clean", "()V")));
        assertEquals(1L, registry.droppedRegistrations());

        metrics.entered(id); metrics.normalCompletion(id, 1); metrics.traceArguments(id, 1, new Object[]{"a"});
        metrics.entered(id); metrics.normalCompletion(id, 1); metrics.traceArguments(id, 1, new Object[]{"b"});
        metrics.suppressedReentrantCallback();
        metrics.reset();

        assertEquals(1, registry.size());
        metrics.entered(id); metrics.normalCompletion(id, 1); metrics.traceArguments(id, 1, new Object[]{"a"});
        Map<String,Object> report = metrics.report();
        Map<?,?> method = (Map<?,?>)((List<?>)report.get("methods")).get(0);
        assertEquals(1L, ((Number)method.get("invocations")).longValue());
        assertEquals(1L, ((Number)((Map<?,?>)((List<?>)method.get("argumentGroups")).get(0)).get("invocations")).longValue());
        assertEquals(0L, ((Number)method.get("droppedArgumentGroups")).longValue());
        assertEquals(0L, ((Number)method.get("overflowArgumentInvocations")).longValue());
        assertEquals(0L, ((Number)report.get("suppressedReentrantCallbacks")).longValue());
        assertEquals(0L, ((Number)report.get("droppedMethodRegistrations")).longValue());
    }

    @Test void concurrentDistinctKeysNeverExceedConfiguredGroupLimit() throws Exception {
        MethodRegistry registry=new MethodRegistry(8); MethodMetrics metrics=new MethodMetrics(registry,4);
        int id=registry.register(new MethodKey("test","Example","clean","(Ljava/lang/Object;)V"));
        List<Thread> threads=new ArrayList<>();
        for(int i=0;i<100;i++){final int value=i;Thread thread=new Thread(()->{
            metrics.entered(id);metrics.normalCompletion(id,1);metrics.traceArguments(id,1,new Object[]{value});
        });threads.add(thread);thread.start();}
        for(Thread thread:threads)thread.join();
        Map<?,?> method=(Map<?,?>)((List<?>)metrics.report().get("methods")).get(0);
        assertTrue(((List<?>)method.get("argumentGroups")).size()<=4);
        long grouped=((List<?>)method.get("argumentGroups")).stream()
                .map(Map.class::cast).mapToLong(group->((Number)group.get("invocations")).longValue()).sum();
        assertEquals(100L,grouped+((Number)method.get("overflowArgumentInvocations")).longValue());
    }

    @Test void liveSnapshotsNeverExposeMoreGroupedCallsThanParentInvocations() throws Exception {
        MethodRegistry registry=new MethodRegistry(8); MethodMetrics metrics=new MethodMetrics(registry,32);
        int id=registry.register(new MethodKey("test","Example","clean","(I)V"));
        java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean(true);
        List<Thread> workers=new ArrayList<>();
        for(int t=0;t<4;t++){Thread worker=new Thread(()->{for(int i=0;i<5000;i++){
            metrics.entered(id);metrics.normalCompletion(id,1);metrics.traceArguments(id,1,new Object[]{i&7});
        }});workers.add(worker);worker.start();}
        while(workers.stream().anyMatch(Thread::isAlive)){
            Map<?,?> method=(Map<?,?>)((List<?>)metrics.report().get("methods")).get(0);
            long invocations=((Number)method.get("invocations")).longValue();
            long grouped=method.get("argumentGroups") instanceof List<?> ? ((List<?>)method.get("argumentGroups")).stream()
                    .map(Map.class::cast).mapToLong(group->((Number)group.get("invocations")).longValue()).sum() : 0L;
            long overflow=method.get("overflowArgumentInvocations") instanceof Number ? ((Number)method.get("overflowArgumentInvocations")).longValue() : 0L;
            assertTrue(grouped+overflow<=invocations,"grouped calls exceeded parent invocation snapshot");
        }
        for(Thread worker:workers)worker.join();
    }

    @Test void bridgeDoesNotRecordArgumentsWhenEntryWasSuppressed() {
        MethodRegistry registry=new MethodRegistry(8); MethodMetrics metrics=new MethodMetrics(registry,4);
        int id=registry.register(new MethodKey("test","Example","clean","(Ljava/lang/Object;)V"));
        MethodProbeBridge.configure(metrics);
        try {
            metrics.entered(id);
            MethodProbeBridge.traceArguments(id,0L,new Object[]{"must-not-appear"});
            Map<?,?> method=(Map<?,?>)((List<?>)metrics.report().get("methods")).get(0);
            assertFalse(method.containsKey("argumentGroups"));
        } finally { MethodProbeBridge.clear(); }
    }

}
