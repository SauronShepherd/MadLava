package com.madlava.reporting;

import com.madlava.config.AgentOptions;
import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.RuntimeConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonlReporterConfigurationTest {
    @TempDir Path temporary;

    @Test
    void unusableOutputDirectoryIsRejectedBeforeConfigurationCommit() throws Exception {
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);
        JsonlReporter reporter = new JsonlReporter(runtime, temporary.resolve("initial"));
        reporter.bindConfiguration(configuration);
        reporter.start(60, true);
        try {
            Path regularFile = temporary.resolve("not-a-directory");
            Files.writeString(regularFile, "x");
            long before = configuration.current().version();
            Path reportBefore = reporter.reportPath();

            RuntimeConfigurationManager.UpdateResult result = configuration.reload(
                    Map.of("output.directory", regularFile.toString()), Map.of(), "test");

            assertFalse(result.applied());
            assertEquals(before, configuration.current().version());
            assertEquals(reportBefore, reporter.reportPath());
        } finally {
            reporter.close();
        }
    }
    @Test
    void outputRotationTransfersRunOwnershipLock() throws Exception {
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);
        JsonlReporter reporter = new JsonlReporter(runtime, temporary.resolve("initial"));
        reporter.bindConfiguration(configuration);
        reporter.start(60, true);
        Path initialRun = reporter.reportPath().getParent();
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@",2)[0];
        Path initialDiscovery = initialRun.getParent().resolve("madlava-run-" + pid + ".json");
        assertTrue(Files.isRegularFile(initialDiscovery));
        Path nextRoot = temporary.resolve("rotated");
        Path nextRun = null;
        try {
            RuntimeConfigurationManager.UpdateResult result = configuration.reload(
                    Map.of("output.directory", nextRoot.toString()), Map.of(), "test");
            assertTrue(result.applied());
            nextRun = reporter.reportPath().getParent();
            assertEquals(nextRoot.toAbsolutePath().normalize(), nextRun.getParent());
            assertTrue(Files.exists(nextRun.resolve("madlava.run.lock")));
            assertFalse(Files.exists(initialDiscovery));
            assertTrue(Files.isRegularFile(nextRoot.resolve("madlava-run-" + pid + ".json")));

            // The old run must no longer be owned once the writer has moved.
            try (FileChannel channel = FileChannel.open(initialRun.resolve("madlava.run.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.tryLock()) {
                assertNotNull(ignored);
            }

            // The active run must remain owned by this reporter.
            try (FileChannel channel = FileChannel.open(nextRun.resolve("madlava.run.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                assertThrows(OverlappingFileLockException.class, channel::tryLock);
            }
        } finally {
            reporter.close();
        }

        // Closing the reporter releases ownership of the rotated run too.
        assertNotNull(nextRun);
        try (FileChannel channel = FileChannel.open(nextRun.resolve("madlava.run.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.tryLock()) {
            assertNotNull(ignored);
        }
    }

    @Test void discoveryManifestEscapesControlCharactersInOutputPath() {
        String manifest=JsonlReporter.discoveryManifestText("123","line\nbreak/madlava.jsonl");
        assertTrue(manifest.contains("line\\nbreak"));
        assertFalse(manifest.contains("line\nbreak"));
        assertEquals(1,manifest.lines().count());
    }


    @Test void outputReloadBeforeStartRelocatesWithoutImplicitlyStartingReporter() throws Exception {
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);
        JsonlReporter reporter = new JsonlReporter(runtime, temporary.resolve("prestart-initial"));
        Path abandonedInitialRun = reporter.reportPath().getParent();
        reporter.bindConfiguration(configuration);
        Path nextRoot = temporary.resolve("prestart-next");
        RuntimeConfigurationManager.UpdateResult result = configuration.reload(
                Map.of("output.directory", nextRoot.toString()), Map.of(), "test");
        assertTrue(result.applied());
        assertFalse(Files.exists(abandonedInitialRun));
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@",2)[0];
        Path discovery = nextRoot.resolve("madlava-run-" + pid + ".json");
        assertFalse(Files.exists(discovery));
        reporter.start(60, true);
        assertTrue(Files.isRegularFile(discovery));
        reporter.close();
    }

    @Test void failedWriterStartDoesNotPublishDiscoveryManifest() throws Exception {
        Path root = temporary.resolve("failed-start");
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);
        JsonlReporter reporter = new JsonlReporter(runtime, root);
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@",2)[0];
        Path discovery = root.resolve("madlava-run-" + pid + ".json");
        Path failedRun = reporter.reportPath().getParent();
        Files.createDirectory(reporter.reportPath());
        assertThrows(IllegalStateException.class, () -> reporter.start(60, true));
        assertFalse(Files.exists(discovery));
        assertFalse(Files.exists(failedRun));
        reporter.close();
    }

    @Test void reporterStartIsIdempotentAndCannotRestartAfterClose() throws Exception {
        Path root = temporary.resolve("lifecycle");
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);
        JsonlReporter reporter = new JsonlReporter(runtime, root);
        assertThrows(IllegalArgumentException.class, () -> reporter.start(0, false));
        reporter.start(60, true);
        Path path = reporter.reportPath();
        reporter.start(60, true); // idempotent: no second writer/schedule/run directory
        assertEquals(path, reporter.reportPath());
        reporter.close();
        assertThrows(IllegalStateException.class, () -> reporter.start(60, true));
    }

    @Test void separateReportersNeverReuseTheSameRunDirectory() throws Exception {
        Path root=temporary.resolve("same-root");
        RuntimeConfigurationManager configuration=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        AgentRuntime runtime=new AgentRuntime("test","hash",AgentOptions.parse("diagnosticsToStderr=false"),null,null,null,configuration,false);
        JsonlReporter first=new JsonlReporter(runtime,root);
        JsonlReporter second=new JsonlReporter(runtime,root);
        try {
            assertNotEquals(first.reportPath().getParent(),second.reportPath().getParent());
        } finally { first.close(); second.close(); }
    }
}
