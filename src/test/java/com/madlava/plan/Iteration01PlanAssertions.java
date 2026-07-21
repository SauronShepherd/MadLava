package com.madlava.plan;

import static org.junit.jupiter.api.Assertions.*;

import com.madlava.core.FeatureRegistry;
import com.madlava.core.FeatureState;
import com.madlava.reporting.BoundedSnapshotQueue;
import java.nio.file.Files;
import java.nio.file.Path;

final class Iteration01PlanAssertions {
    private Iteration01PlanAssertions() {}

    static void contract(String task) throws Exception {
        assertTrue(Files.readString(Path.of("BUILD-PLAN.md")).contains("#### " + task + " -"), task);
        if (task.startsWith("I01-S01")) {
            String pom = Files.readString(Path.of("pom.xml"));
            assertTrue(pom.contains("<maven.compiler.release>11</maven.compiler.release>"));
            assertTrue(pom.contains("<id>generic</id>"));
            assertTrue(Files.exists(Path.of("LICENSE")) && Files.exists(Path.of("SECURITY.md")));
        } else if (task.startsWith("I01-S02")) {
            FeatureRegistry registry = new FeatureRegistry();
            registry.register("baseline", FeatureState.INITIALIZING);
            registry.transition("baseline", FeatureState.RUNNING);
            assertEquals(FeatureState.RUNNING, registry.snapshot().get("baseline"));
            BoundedSnapshotQueue queue = new BoundedSnapshotQueue(1);
            queue.submit("first");
            queue.submit("second");
            assertEquals(1, queue.droppedCount());
            assertTrue(Files.readString(Path.of("src/main/resources/schema/madlava-report-v3.schema.json")).contains("\"const\":3"));
        } else {
            assertTrue(Files.exists(Path.of("scripts/certify-i01.ps1")));
            assertTrue(Files.exists(Path.of("docs/getting-started.md")));
            assertTrue(Files.exists(Path.of("report-viewer/index.html")));
        }
    }

    static void negative(String task) throws Exception {
        FeatureRegistry registry = new FeatureRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register("", FeatureState.RUNNING));
        registry.register("baseline", FeatureState.RUNNING);
        assertThrows(IllegalArgumentException.class, () -> registry.register("baseline", FeatureState.RUNNING));
        String agent = Files.readString(Path.of("src/main/java/com/madlava/agent/MadLavaAgent.java"));
        assertFalse(agent.contains("System.exit("), task);
        String viewer = Files.readString(Path.of("report-viewer/viewer.js"));
        assertFalse(viewer.matches("(?s).*\\beval\\s*\\(.*"), task);
        assertFalse(viewer.contains("innerHTML"), task);
    }
}
