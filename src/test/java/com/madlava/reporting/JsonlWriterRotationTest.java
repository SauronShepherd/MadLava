package com.madlava.reporting;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonlWriterRotationTest {
    @Test void rotationUsesTheNewDestination() throws Exception {
        Path root=Files.createTempDirectory("madlava-rotation");
        BoundedSnapshotQueue queue=new BoundedSnapshotQueue(16);
        JsonlWriter writer=new JsonlWriter(queue,root.resolve("a").resolve("madlava.jsonl"));
        writer.start(); queue.submit("{\"record\":\"before\"}");
        Thread.sleep(100);
        Path next=root.resolve("b").resolve("madlava.jsonl"); writer.rotate(next);
        queue.submit("{\"record\":\"after\"}"); Thread.sleep(100); writer.close();
        assertTrue(Files.exists(next));
        assertTrue(Files.walk(root.resolve("b")).anyMatch(path -> path.toString().endsWith(".jsonl")));
        assertTrue(Files.walk(root.resolve("a")).anyMatch(path -> path.toString().endsWith(".jsonl")));
    }
}
