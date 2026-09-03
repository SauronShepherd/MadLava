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
    @Test void sizeRolloverKeepsDocumentedActivePathAndBuildsWholeRunManifest() throws Exception {
        Path root=Files.createTempDirectory("madlava-size-rotation");Path active=root.resolve("madlava.jsonl");
        BoundedSnapshotQueue queue=new BoundedSnapshotQueue(128);JsonlWriter writer=new JsonlWriter(queue,active,80);
        writer.start();for(int i=0;i<30;i++)queue.submit("{\"record\":"+i+",\"payload\":\"xxxxxxxxxxxxxxxx\"}");writer.close();
        assertTrue(Files.isRegularFile(active));
        try(java.util.stream.Stream<Path> files=Files.list(root.resolve("segments"))){assertTrue(files.anyMatch(Files::isRegularFile));}
        String manifest=Files.readString(root.resolve("madlava-report-manifest.json"));
        assertTrue(manifest.contains("\"state\":\"FINAL\""));assertTrue(manifest.contains("\"records\":30"));
    }

    @Test void finalManifestEscapesControlCharactersInReportPath() {
        String manifest=JsonlWriter.finalManifestText("line\nbreak/madlava.jsonl",1,1,12,"abc");
        assertTrue(manifest.contains("line\\nbreak"));
        assertFalse(manifest.contains("line\nbreak"));
        assertEquals(1,manifest.lines().count());
    }

    @Test void startRejectsUnopenableReportPathSynchronously() throws Exception {
        Path root = Files.createTempDirectory("madlava-start-preflight");
        Path active = root.resolve("madlava.jsonl");
        Files.createDirectory(active);
        JsonlWriter writer = new JsonlWriter(new BoundedSnapshotQueue(4), active);
        assertThrows(java.io.IOException.class, writer::start);
        assertFalse(writer.isWorkerAlive());
    }

    @Test void failedRotationLeavesCurrentWriterHealthy() throws Exception {
        Path root = Files.createTempDirectory("madlava-rotation-preflight");
        Path active = root.resolve("a").resolve("madlava.jsonl");
        BoundedSnapshotQueue queue = new BoundedSnapshotQueue(16);
        JsonlWriter writer = new JsonlWriter(queue, active);
        writer.start();
        queue.submit("{\"record\":\"before\"}");
        Thread.sleep(60);

        Path unusable = root.resolve("b").resolve("madlava.jsonl");
        Files.createDirectories(unusable); // A directory cannot be opened as the JSONL file.
        assertThrows(java.io.IOException.class, () -> writer.rotate(unusable));

        queue.submit("{\"record\":\"after\"}");
        Thread.sleep(60);
        writer.close();
        String text = Files.readString(active);
        assertTrue(text.contains("before"));
        assertTrue(text.contains("after"));
    }

}
