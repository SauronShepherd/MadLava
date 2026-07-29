package com.madlava.agent;

import com.madlava.config.AgentOptions;
import com.madlava.instrumentation.MadLavaTransformer;
import com.madlava.methods.MethodFilter;
import com.madlava.methods.MethodMetrics;
import com.madlava.methods.MethodProbeBridge;
import com.madlava.methods.MethodRegistry;
import com.madlava.reporting.AgentRuntime;
import com.madlava.reporting.JsonlReporter;
import com.madlava.serialization.SparkSerializationBridge;
import com.madlava.serialization.SparkSerializationMetrics;
import com.madlava.serialization.SparkSerializationPlan;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.TreeMap;

/** MadLava Iteration-12 Java agent entry point. */
public final class MadLavaAgent {
    private static final String FALLBACK_VERSION = "0.1.0";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private MadLavaAgent() {
    }

    public static java.nio.file.Path parseOutput(String arguments) {
        return AgentOptions.parse(arguments).outputDirectory();
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        start(arguments, instrumentation, false);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        start(arguments, instrumentation, true);
    }

    private static void start(
            String rawArguments,
            Instrumentation instrumentation,
            boolean retransformLoadedClasses) {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        MadLavaTransformer transformer = null;
        JsonlReporter reporter = null;
        boolean methodBridgeConfigured = false;
        boolean serializationBridgeConfigured = false;
        try {
            AgentOptions options = AgentOptions.parse(rawArguments);
            String version = MadLavaAgent.class.getPackage().getImplementationVersion();
            if (version == null || version.isBlank()) {
                version = FALLBACK_VERSION;
            }

            MethodFilter methodFilter = MethodFilter.parse(
                    options.methodIncludes(),
                    options.methodExcludes());
            MethodRegistry methodRegistry = options.methodProfilingEnabled()
                    ? new MethodRegistry(options.methodMaxEntries())
                    : null;
            MethodMetrics methodMetrics = methodRegistry == null
                    ? null
                    : new MethodMetrics(methodRegistry);
            if (methodMetrics != null) {
                MethodProbeBridge.configure(methodMetrics);
                methodBridgeConfigured = true;
            }

            SparkSerializationPlan serializationPlan = options.sparkSerializationEnabled()
                    ? new SparkSerializationPlan(options.sparkSerializationProfile())
                    : null;
            SparkSerializationMetrics serializationMetrics = serializationPlan == null
                    ? null
                    : new SparkSerializationMetrics(options.sparkSerializationMaxGroups());
            if (serializationMetrics != null) {
                SparkSerializationBridge.configure(
                        serializationPlan,
                        serializationMetrics,
                        options.sparkSerializationRootClasses());
                serializationBridgeConfigured = true;
            }

            if (options.methodProfilingEnabled() || options.sparkSerializationEnabled()) {
                transformer = new MadLavaTransformer(
                        options.methodProfilingEnabled(),
                        methodFilter,
                        methodRegistry == null ? new MethodRegistry(1) : methodRegistry,
                        options.sparkSerializationEnabled(),
                        serializationPlan == null
                                ? new SparkSerializationPlan(options.sparkSerializationProfile())
                                : serializationPlan);
                instrumentation.addTransformer(transformer, true);
            }

            AgentRuntime runtime = new AgentRuntime(
                    version,
                    sha256(canonical(options.effectiveMap())),
                    options,
                    methodMetrics,
                    serializationMetrics,
                    serializationPlan);
            reporter = new JsonlReporter(runtime, options.outputDirectory());
            reporter.start(options.snapshotIntervalSeconds(), options.shutdownSnapshotOnly());

            MadLavaTransformer activeTransformer = transformer;
            if (retransformLoadedClasses && activeTransformer != null) {
                retransformAlreadyLoaded(instrumentation, activeTransformer);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(reporter::close, "madlava-shutdown"));
            if (options.diagnosticsToStderr()) {
                System.err.println(
                        "MadLava " + version
                                + " Iteration-12 ready; report="
                                + reporter.reportPath().toAbsolutePath().normalize());
            }
        } catch (Throwable failure) {
            if (reporter != null) {
                try { reporter.close(); } catch (Throwable ignored) { }
            }
            if (transformer != null) {
                try { instrumentation.removeTransformer(transformer); } catch (Throwable ignored) { }
            }
            if (methodBridgeConfigured) {
                try { MethodProbeBridge.clear(); } catch (Throwable ignored) { }
            }
            if (serializationBridgeConfigured) {
                try { SparkSerializationBridge.clear(); } catch (Throwable ignored) { }
            }
            STARTED.set(false);
            System.err.println(
                    "MadLava Iteration-12 bootstrap disabled: "
                            + failure.getClass().getSimpleName());
        }
    }

    private static String canonical(Map<String, String> values) {
        StringBuilder output = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            output.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return output.toString();
    }

    private static void retransformAlreadyLoaded(
            Instrumentation instrumentation,
            MadLavaTransformer transformer) {
        if (!instrumentation.isRetransformClassesSupported()) {
            return;
        }
        for (Class<?> candidate : instrumentation.getAllLoadedClasses()) {
            try {
                if (candidate == null || !instrumentation.isModifiableClass(candidate)) {
                    continue;
                }
                String internalName = candidate.getName().replace('.', '/');
                if (transformer.mayTransformClass(internalName)) {
                    instrumentation.retransformClasses(candidate);
                }
            } catch (Throwable ignored) {
                // A single class must never disable the agent or the application.
            }
        }
    }

    public static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder output = new StringBuilder(digest.length * 2);
        for (byte current : digest) {
            output.append(String.format("%02x", current));
        }
        return output.toString();
    }
}
