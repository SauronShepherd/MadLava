package com.madlava.reporting;

import com.madlava.core.FeatureRegistry;
import com.madlava.runtime.RuntimeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotSchedulerTest {
    @TempDir Path temporary;

    @Test
    void closeAlwaysEmitsOneFinalSnapshotAndIsIdempotent() {
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue(8);
        SnapshotScheduler scheduler = new SnapshotScheduler(context(), queue, "test", "hash");

        scheduler.close();
        String finalSnapshot = queue.poll();
        assertNotNull(finalSnapshot);
        assertTrue(finalSnapshot.contains("\"final\":true"));

        scheduler.close();
        assertNull(queue.poll());
    }

    @Test
    void startRejectsInvalidIntervalAndStartAfterClose() {
        SnapshotScheduler scheduler = new SnapshotScheduler(context(), new BoundedSnapshotQueue(8), "test", "hash");
        assertThrows(IllegalArgumentException.class, () -> scheduler.start(0));
        scheduler.close();
        assertThrows(IllegalStateException.class, () -> scheduler.start(1));
    }

    private RuntimeContext context() {
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    return null;
                });
        return new RuntimeContext(instrumentation, Clock.systemUTC(), temporary, new FeatureRegistry());
    }
}
