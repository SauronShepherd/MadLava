package com.madlava.verification.spark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forked-JVM end-to-end tests against the selected real Apache Spark 3.5 or 4 artifact. */
final class SparkSerializerAgentIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EXPECTED_SPARK_VERSION = requiredProperty("madlava.spark.expected.version");
    private static final String EXPECTED_SCALA_BINARY = requiredProperty("madlava.spark.expected.scala");
    private static final String METHOD_FILTER = String.join(";",
            "org.apache.spark.serializer.JavaSerializer.*",
            "org.apache.spark.serializer.JavaSerializerInstance.*",
            "org.apache.spark.serializer.JavaSerializationStream.*",
            "org.apache.spark.serializer.JavaDeserializationStream.*",
            "org.apache.spark.serializer.KryoSerializer.*",
            "org.apache.spark.serializer.KryoSerializerInstance.*",
            "org.apache.spark.serializer.KryoSerializationStream.*",
            "org.apache.spark.serializer.KryoDeserializationStream.*");

    @TempDir
    Path temporaryDirectory;

    @Test
    void javaSerializerIsObservedByGenericMethodTracingAndSparkAnalysis() throws Exception {
        Execution execution = execute("java");
        assertTrue(execution.stdout.contains("MADLAVA_SPARK_SERIALIZER_OK=java"), execution.combined());
        JsonNode snapshot = execution.snapshot;

        assertMethodObserved(snapshot, "org.apache.spark.serializer.JavaSerializerInstance", "serialize");
        assertMethodObserved(snapshot, "org.apache.spark.serializer.JavaSerializerInstance", "deserialize");
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.JavaSerializerInstance", "SERIALIZE", "BOUNDARY", true, false);
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.JavaSerializerInstance", "DESERIALIZE", "BOUNDARY", true, false);
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.JavaSerializationStream", "WRITE_OBJECT", "STREAM", false, false);
        assertCoverageHealthy(snapshot);
    }

    @Test
    void kryoSerializerIsObservedByGenericMethodTracingAndSparkAnalysis() throws Exception {
        Execution execution = execute("kryo");
        assertTrue(execution.stdout.contains("MADLAVA_SPARK_SERIALIZER_OK=kryo"), execution.combined());
        JsonNode snapshot = execution.snapshot;

        assertMethodObserved(snapshot, "org.apache.spark.serializer.KryoSerializerInstance", "serialize");
        assertMethodObserved(snapshot, "org.apache.spark.serializer.KryoSerializerInstance", "deserialize");
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.KryoSerializerInstance", "SERIALIZE", "BOUNDARY", true, false);
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.KryoSerializerInstance", "DESERIALIZE", "BOUNDARY", true, false);
        assertSerializationGroup(snapshot, "org.apache.spark.serializer.KryoSerializationStream", "WRITE_OBJECT", "STREAM", false, false);
        assertCoverageHealthy(snapshot);
    }

    @Test
    void realSparkKryoFailureIsReportedAsExceptionalWithoutBreakingTheChildContract() throws Exception {
        Execution execution = execute("kryo-failure");
        assertTrue(execution.stdout.contains("failureObserved=true"), execution.combined());
        JsonNode groups = execution.snapshot.path("features").path("sparkSerialization").path("groups");
        boolean failureFound = false;
        for (JsonNode group : groups) {
            if (group.path("implementation").asText().equals("org.apache.spark.serializer.KryoSerializerInstance")
                    && group.path("operation").asText().equals("SERIALIZE")
                    && group.path("failedOperations").asLong() > 0L) {
                failureFound = true;
            }
        }
        assertTrue(failureFound, execution.snapshot.toPrettyString());

        JsonNode methods = execution.snapshot.path("features").path("methodProfiling").path("methods");
        boolean exceptionalMethodFound = false;
        for (JsonNode method : methods) {
            if (method.path("owner").asText().equals("org.apache.spark.serializer.KryoSerializerInstance")
                    && method.path("method").asText().equals("serialize")
                    && method.path("exceptionalCompletions").asLong() > 0L) {
                exceptionalMethodFound = true;
            }
        }
        assertTrue(exceptionalMethodFound, execution.snapshot.toPrettyString());
    }

    private Execution execute(String engine) throws Exception {
        Path output = temporaryDirectory.resolve(engine);
        Files.createDirectories(output);
        Path agent = Path.of(System.getProperty("madlava.agent.jar"));
        assertTrue(Files.isRegularFile(agent), "Missing packaged agent: " + agent);

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable("java")).toString());
        addJava17ModuleOpens(command);
        command.add("-Dmadlava.spark.expected.version=" + EXPECTED_SPARK_VERSION);
        command.add("-Dmadlava.spark.expected.scala=" + EXPECTED_SCALA_BINARY);
        command.add("-javaagent:" + agent.toAbsolutePath() + "="
                + "output=" + output.toAbsolutePath()
                + ",shutdownSnapshotOnly=true"
                + ",diagnosticsToStderr=true"
                + ",methodProfiling=true"
                + ",methodInclude=" + METHOD_FILTER
                + ",sparkSerialization=true"
                + ",sparkSerializationProfile=ALL"
                + ",sparkSerializationRootClasses=true");
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(SparkSerializerProcessApp.class.getName());
        command.add(engine);

        Path stdoutLog = output.resolve("child.stdout.log");
        Path stderrLog = output.resolve("child.stderr.log");
        Process process = new ProcessBuilder(command)
                .redirectOutput(stdoutLog.toFile())
                .redirectError(stderrLog.toFile())
                .start();
        boolean finished = process.waitFor(Duration.ofSeconds(120).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("Spark serializer child process timed out\n"
                    + readLog(stdoutLog) + System.lineSeparator() + readLog(stderrLog));
        }
        String stdout = readLog(stdoutLog);
        String stderr = readLog(stderrLog);
        assertEquals(0, process.exitValue(), stdout + System.lineSeparator() + stderr);
        assertTrue(stdout.contains("sparkVersion=" + EXPECTED_SPARK_VERSION), stdout + System.lineSeparator() + stderr);
        assertTrue(stdout.contains("scalaVersion=" + EXPECTED_SCALA_BINARY + "."), stdout + System.lineSeparator() + stderr);

        Path report = output.resolve("madlava.jsonl");
        if (!Files.isRegularFile(report)) {
            Path expectedReport = report;
            try (var reports = Files.walk(output)) {
                report = reports.filter(candidate -> candidate.getFileName().toString().equals("madlava.jsonl"))
                        .findFirst().orElse(expectedReport);
            }
        }
        assertTrue(Files.isRegularFile(report), "Missing report: " + report);
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "Empty report: " + report);
        JsonNode snapshot = JSON.readTree(lines.get(lines.size() - 1));
        String reportText = snapshot.toString();
        assertFalse(reportText.contains("MADLAVA_PRIVATE_PAYLOAD_12"),
                "Payload value leaked into MadLava JSONL");
        assertFalse(reportText.contains("MADLAVA_PRIVATE_FAILURE_PAYLOAD_12"),
                "Failure payload value leaked into MadLava JSONL");
        return new Execution(stdout, stderr, snapshot);
    }

    private static String readLog(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    private static void assertMethodObserved(JsonNode snapshot, String owner, String methodName) {
        JsonNode methods = snapshot.path("features").path("methodProfiling").path("methods");
        for (JsonNode method : methods) {
            if (method.path("owner").asText().equals(owner)
                    && method.path("method").asText().equals(methodName)
                    && method.path("invocations").asLong() > 0L
                    && method.path("normalCompletions").asLong() > 0L) {
                return;
            }
        }
        throw new AssertionError("Missing real method observation: " + owner + '.' + methodName
                + System.lineSeparator() + snapshot.toPrettyString());
    }

    private static void assertSerializationGroup(
            JsonNode snapshot,
            String implementation,
            String operation,
            String layer,
            boolean exactBytesRequired,
            boolean failureRequired) {
        JsonNode groups = snapshot.path("features").path("sparkSerialization").path("groups");
        for (JsonNode group : groups) {
            if (!group.path("implementation").asText().equals(implementation)
                    || !group.path("operation").asText().equals(operation)
                    || !group.path("layer").asText().equals(layer)) {
                continue;
            }
            assertTrue(group.path("operations").asLong() > 0L, group.toPrettyString());
            assertTrue(group.path("totalDurationNanos").asLong() >= 0L, group.toPrettyString());
            if (!operation.endsWith("STREAM_FACTORY")) {
                assertTrue(group.path("rootClass").asText().contains("SparkSerializerProcessApp$Payload"),
                        "Expected only the bounded root class identity, not payload values: " + group.toPrettyString());
            }
            if (exactBytesRequired) {
                assertTrue(group.path("operationsWithObservedBytes").asLong() > 0L, group.toPrettyString());
                assertTrue(group.path("observedBytes").asLong() > 0L, group.toPrettyString());
                assertTrue(group.path("byteAccuracy").asText().startsWith("EXACT_"), group.toPrettyString());
            } else {
                assertEquals("UNAVAILABLE", group.path("byteAccuracy").asText(), group.toPrettyString());
            }
            if (failureRequired) {
                assertTrue(group.path("failedOperations").asLong() > 0L, group.toPrettyString());
            }
            return;
        }
        throw new AssertionError("Missing serialization group " + implementation + '/' + operation + '/' + layer
                + System.lineSeparator() + snapshot.toPrettyString());
    }

    private static void assertCoverageHealthy(JsonNode snapshot) {
        JsonNode serialization = snapshot.path("features").path("sparkSerialization");
        assertEquals("RUNNING", serialization.path("state").asText());
        assertEquals(0L, serialization.path("bridgeFailures").asLong(), serialization.toPrettyString());
        JsonNode coverage = serialization.path("coverage");
        assertTrue(coverage.path("transformedClasses").asLong() > 0L, coverage.toPrettyString());
        assertEquals(0L, coverage.path("transformationFailures").asLong(), coverage.toPrettyString());
        assertNotNull(coverage.path("targets"));
        assertEquals("SPARK_3_5_AND_4_EXACT_SERIALIZER_SIGNATURES", coverage.path("adapter").asText());
        JsonNode runtime = serialization.path("runtime");
        assertEquals(EXPECTED_SPARK_VERSION, runtime.path("sparkVersion").asText(), runtime.toPrettyString());
        assertTrue(runtime.path("scalaVersion").asText().startsWith(EXPECTED_SCALA_BINARY + "."), runtime.toPrettyString());
        assertTrue(runtime.path("supported").asBoolean(), runtime.toPrettyString());
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing integration-test property: " + name);
        }
        return value;
    }

    private static void addJava17ModuleOpens(List<String> command) {
        int feature = Runtime.version().feature();
        if (feature < 17) {
            return;
        }
        for (String packageName : List.of(
                "java.lang",
                "java.lang.invoke",
                "java.lang.reflect",
                "java.io",
                "java.net",
                "java.nio",
                "java.util",
                "java.util.concurrent",
                "sun.nio.ch")) {
            command.add("--add-opens=java.base/" + packageName + "=ALL-UNNAMED");
        }
    }

    private static String executable(String base) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? base + ".exe" : base;
    }

    private static final class Execution {
        private final String stdout;
        private final String stderr;
        private final JsonNode snapshot;

        private Execution(String stdout, String stderr, JsonNode snapshot) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.snapshot = snapshot;
        }

        private String combined() {
            return stdout + System.lineSeparator() + stderr + System.lineSeparator() + snapshot.toPrettyString();
        }
    }
}
