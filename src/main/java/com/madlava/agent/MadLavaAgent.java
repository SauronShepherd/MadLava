package com.madlava.agent;

import com.madlava.core.FeatureRegistry;
import com.madlava.core.FeatureState;
import com.madlava.diagnostics.DiagnosticsRuntime;
import com.madlava.instrumentation.CompositeTransformer;
import com.madlava.probes.ProbeBridge;
import com.madlava.reporting.BoundedSnapshotQueue;
import com.madlava.reporting.JsonlWriter;
import com.madlava.reporting.SnapshotScheduler;
import com.madlava.runtime.RuntimeContext;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Clock;

public final class MadLavaAgent {
    private static final String FALLBACK_VERSION = "0.1.0";
    private static final String[] BASE_FEATURES = {"heapUsage", "nonHeapUsage", "bufferPools", "garbageCollection", "threadStatistics", "threadCpu", "processResources", "classLoaderInsights", "jvmExecutionEngine", "selfObservability"};
    private static final String[] RUNTIME_FEATURES = {"streamIo", "networkIo", "serialization", "threadPools"};
    private static final String[] DIAGNOSTIC_FEATURES = {"executionSampling", "allocationProfiling", "contentionProfiling", "threadDumps", "heapDiagnostics", "offHeapDiagnostics", "incidentRecording", "overheadControl"};

    private MadLavaAgent() {}
    public static void premain(String args, Instrumentation instrumentation) { start(args, instrumentation); }
    public static void agentmain(String args, Instrumentation instrumentation) { start(args, instrumentation); }

    private static void start(String args, Instrumentation instrumentation) {
        try {
            String version = MadLavaAgent.class.getPackage().getImplementationVersion();
            if (version == null) version = FALLBACK_VERSION;
            Path output = parseOutput(args);
            FeatureRegistry registry = new FeatureRegistry();
            register(registry, BASE_FEATURES);
            String include = option(args, "instrumentationInclude");
            if (include != null && !include.isBlank()) {
                instrumentation.addTransformer(new CompositeTransformer(include), false);
                ProbeBridge.configureJfr(Boolean.parseBoolean(option(args, "jfrThrowables")));
                register(registry, "instanceCounting", "throwables");
            }
            if (enabled(args, "runtimeObservation")) register(registry, RUNTIME_FEATURES);
            if (enabled(args, "diagnostics")) { DiagnosticsRuntime.start(output); register(registry, DIAGNOSTIC_FEATURES); }
            if (enabled(args, "sparkObservation")) register(registry, "spark");
            RuntimeContext context = new RuntimeContext(instrumentation, Clock.systemUTC(), output, registry);
            BoundedSnapshotQueue queue = new BoundedSnapshotQueue(64);
            JsonlWriter writer = new JsonlWriter(queue, output.resolve("madlava.jsonl"));
            writer.start();
            SnapshotScheduler scheduler = new SnapshotScheduler(context, queue, version, sha256(args == null ? "" : args));
            scheduler.start(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { scheduler.close(); ProbeBridge.shutdownJfr(); writer.close(); }, "madlava-shutdown"));
            System.err.println("MadLava " + version + " ready; output=" + output.toAbsolutePath().normalize());
        } catch (Throwable failure) {
            System.err.println("MadLava bootstrap disabled: " + failure.getClass().getSimpleName());
        }
    }

    private static boolean enabled(String args, String key) { return Boolean.parseBoolean(option(args, key)); }
    private static void register(FeatureRegistry registry, String... ids) { for (String id : ids) registry.register(id, FeatureState.RUNNING); }
    static Path parseOutput(String args) { String value=option(args,"output");return Paths.get(value==null?"madlava-output":value).toAbsolutePath().normalize(); }
    static String option(String args,String key){if(args!=null)for(String part:args.split(",")){String[] pair=part.split("=",2);if(pair.length==2&&pair[0].trim().equals(key))return pair[1].trim();}return null;}
    static String sha256(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder output=new StringBuilder(64);for(byte valueByte:digest)output.append(String.format("%02x",valueByte));return output.toString();}
}
