package com.madlava.reporting;

import com.madlava.config.AgentOptions;
import com.madlava.methods.MethodMetrics;
import com.madlava.serialization.SparkSerializationMetrics;
import com.madlava.serialization.SparkSerializationPlan;
import com.madlava.features.JvmMetricsCollector;
import com.madlava.probes.ProbeBridge;
import com.madlava.io.RuntimeObservationBridge;
import com.madlava.pools.ObservedExecutorService;
import com.madlava.diagnostics.DiagnosticsRuntime;
import com.madlava.spark.SparkObservationRegistry;
import com.madlava.core.RuntimeCapabilities;
import com.madlava.serialization.SparkRuntimeInfo;
import com.madlava.config.RuntimeConfigurationManager;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Immutable references to bounded feature aggregators. */
public final class AgentRuntime {
    private final String version;
    private final String configurationHash;
    private final AgentOptions options;
    private final MethodMetrics methodMetrics;
    private final SparkSerializationMetrics serializationMetrics;
    private final SparkSerializationPlan serializationPlan;
    private final long startedEpochMillis;
    private final JvmMetricsCollector jvmMetrics = new JvmMetricsCollector();
    private final RuntimeConfigurationManager runtimeConfiguration;
    private final Boolean retransformationSupported;

    public AgentRuntime(
            String version,
            String configurationHash,
            AgentOptions options,
            MethodMetrics methodMetrics,
            SparkSerializationMetrics serializationMetrics,
            SparkSerializationPlan serializationPlan) {
        this(version, configurationHash, options, methodMetrics, serializationMetrics, serializationPlan, null, null);
    }

    public AgentRuntime(
            String version, String configurationHash, AgentOptions options, MethodMetrics methodMetrics,
            SparkSerializationMetrics serializationMetrics, SparkSerializationPlan serializationPlan,
            RuntimeConfigurationManager runtimeConfiguration) {
        this(version, configurationHash, options, methodMetrics, serializationMetrics, serializationPlan, runtimeConfiguration, null);
    }

    public AgentRuntime(
            String version, String configurationHash, AgentOptions options, MethodMetrics methodMetrics,
            SparkSerializationMetrics serializationMetrics, SparkSerializationPlan serializationPlan,
            RuntimeConfigurationManager runtimeConfiguration, Boolean retransformationSupported) {
        this.version = version;
        this.configurationHash = configurationHash;
        this.options = options;
        this.methodMetrics = methodMetrics;
        this.serializationMetrics = serializationMetrics;
        this.serializationPlan = serializationPlan;
        this.runtimeConfiguration = runtimeConfiguration;
        this.retransformationSupported = retransformationSupported;
        this.startedEpochMillis = System.currentTimeMillis();
    }

    public Map<String, Object> snapshot(String reason) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("recordType", "snapshot");
        root.put("timestamp", Instant.now().toString());
        root.put("reason", reason);
        root.put("final", "shutdown".equals(reason));
        root.put("agentVersion", version);
        root.put("startupConfigurationHash", configurationHash);
        RuntimeConfigurationManager.ConfigurationState configurationState = runtimeConfiguration == null
                ? null : runtimeConfiguration.current();
        Map<String, Object> live = configurationState == null
                ? java.util.Collections.emptyMap() : configurationState.values();
        if (configurationState != null) {
            root.put("configurationHash", configurationState.hash());
            root.put("configurationVersion", configurationState.version());
            root.put("runtimeConfigurationHash", configurationState.hash());
            root.put("configurationRuntime", Map.of(
                    "version", configurationState.version(),
                    "successfulReloads", runtimeConfiguration.successfulReloads(),
                    "failedReloads", runtimeConfiguration.failedReloads(),
                    "listenerFailures", runtimeConfiguration.listenerFailures(),
                    "lastReloadEpochMillis", runtimeConfiguration.lastReloadEpochMillis()));
        } else {
            root.put("configurationHash", configurationHash);
        }
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("source", options.configurationSource());
        provenance.put("sourcePath", options.configurationSourcePath());
        provenance.put("hashAlgorithm", "SHA-256");
        root.put("configuration", provenance);
        Map<String, Object> capabilities = new LinkedHashMap<>();
        try {
            RuntimeCapabilities.detect().values().forEach((name, available) ->
                    capabilities.put(name, Map.of("state", available ? "AVAILABLE" : "UNAVAILABLE")));
        } catch (Throwable failure) {
            capabilities.put("runtimeDetection", collectionFailure(failure));
        }
        capabilities.put("jfr", capabilityJfr());
        capabilities.put("bootstrapBridge", Map.of("state", "UNAVAILABLE", "reason", "not-installed-in-0.1.0"));
        capabilities.put("retransformation", retransformationSupported == null
                ? Map.of("state", "UNKNOWN", "reason", "instrumentation-not-provided")
                : Map.of("state", retransformationSupported ? "AVAILABLE" : "UNAVAILABLE", "reason", "instrumentation-capability"));
        root.put("capabilities", capabilities);
        root.put("sparkRuntime", safeMap(SparkRuntimeInfo::detect));

        Map<String, Object> cumulative = new LinkedHashMap<>();
        try {
            cumulative.putAll(jvmMetrics.collect(feature ->
                    bool(live, "features." + feature + ".enabled", true)));
        } catch (Throwable failure) {
            cumulative.put("jvmMetrics", collectionFailure(failure));
        }
        try {
            ProbeBridge.Snapshot probes = ProbeBridge.snapshot();
            cumulative.put("instanceCounting", Map.of(
                    "successfulOutermostConstructors", probes.constructed(),
                    "source", "ProbeBridge",
                    "accuracy", "selected_classes"));
            cumulative.put("throwables", Map.of(
                    "created", probes.throwableCreated(),
                    "explicitThrows", probes.explicitThrows(),
                    "propagations", probes.propagations(),
                    "jfrThrows", probes.jfrThrows(),
                    "jfrState", probes.jfrState(),
                    "payloadCapture", false,
                    "source", "ProbeBridge"));
        } catch (Throwable failure) {
            cumulative.put("instanceCounting", collectionFailure(failure));
            cumulative.put("throwables", collectionFailure(failure));
        }
        try {
            RuntimeObservationBridge.Snapshot observations = RuntimeObservationBridge.snapshot();
            cumulative.put("io", Map.of(
                    "observedLayers", observations.ioReport(),
                    "source", "RuntimeObservationBridge",
                    "payloadCapture", false));
            cumulative.put("serialization", Map.of(
                    "observedLayers", observations.serializationReport(),
                    "source", "RuntimeObservationBridge",
                    "payloadCapture", false));
        } catch (Throwable failure) {
            cumulative.put("io", collectionFailure(failure));
            cumulative.put("serialization", collectionFailure(failure));
        }
        cumulative.put("threadPools", safeMap(() -> ObservedExecutorService.snapshot().report()));
        cumulative.put("diagnostics", safeMap(DiagnosticsRuntime::snapshot));
        cumulative.put("spark", safeMap(() -> SparkObservationRegistry.snapshot(Thread.currentThread().getContextClassLoader())));
        root.put("cumulative", cumulative);
        root.put("pid", processId());
        root.put("uptimeMillis", Math.max(0L, System.currentTimeMillis() - startedEpochMillis));

        Map<String, Object> featureReports = new LinkedHashMap<>();
        if (methodMetrics != null && options.methodProfilingEnabled()) {
            featureReports.put("methodProfiling", safeMap(methodMetrics::report));
        } else {
            featureReports.put("methodProfiling", Map.of("state", "DISABLED"));
        }
        if (serializationMetrics != null && serializationPlan != null) {
            featureReports.put("sparkSerialization", safeMap(() -> serializationMetrics.report(serializationPlan)));
        } else {
            featureReports.put("sparkSerialization", Map.of("state", "DISABLED"));
        }
        root.put("features", featureReports);

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("methodProfiling", options.methodProfilingEnabled());
        configuration.put("methodTracing", bool(live, "features.methodTracing.enabled", options.methodTracingEnabled()));
        configuration.put("methodTracingSampleRate", decimal(live, "features.methodTracing.sampleRate", options.methodTracingSampleRate()));
        configuration.put("methodIncludes", text(live, "filters.methods.includes", options.methodIncludes()));
        configuration.put("methodExcludes", text(live, "filters.methods.excludes", options.methodExcludes()));
        configuration.put("methodMaxEntries", options.methodMaxEntries());
        configuration.put("sparkSerialization", options.sparkSerializationEnabled());
        configuration.put("sparkSerializationProfile", options.sparkSerializationProfile().name());
        configuration.put("sparkSerializationRootClasses", options.sparkSerializationRootClasses());
        configuration.put("sparkSerializationMaxGroups", options.sparkSerializationMaxGroups());
        configuration.put("output", text(live, "output.directory", options.outputDirectory().toString()));
        configuration.put("reportMaxRows", integer(live, "reporting.human.maxRows", options.reportMaxRows()));
        configuration.put("reportTruncate", integer(live, "reporting.human.truncate", options.reportTruncate()));
        for (String section : new String[]{"methodProfiling", "argumentGroups", "sparkSerialization", "sparkSerializationDetail", "diagnostics"})
            configuration.put("reportMaxRows." + section, integer(live, "reporting.human.sections." + section + ".maxRows", options.reportSectionMaxRows(section)));
        root.put("effectiveConfiguration", configuration);
        return root;
    }

    private static Map<String, Object> safeMap(Supplier<? extends Map<String, Object>> supplier) {
        try {
            Map<String, Object> value = supplier.get();
            return value == null ? Map.of("state", "UNAVAILABLE", "reason", "collector-returned-null") : value;
        } catch (Throwable failure) {
            return collectionFailure(failure);
        }
    }

    private static Map<String, Object> collectionFailure(Throwable failure) {
        return Map.of(
                "state", "UNAVAILABLE",
                "reason", "collection-failed",
                "errorType", failure == null ? "unknown" : failure.getClass().getName());
    }

    private static String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key); return value == null ? fallback : String.valueOf(value);
    }
    private static boolean bool(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback;
    }
    private static int integer(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key); return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
    private static double decimal(Map<String, Object> values, String key, double fallback) {
        Object value = values.get(key); return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static Map<String, Object> capabilityJfr() {
        try { Class.forName("jdk.jfr.consumer.RecordingStream", false, AgentRuntime.class.getClassLoader());
            return Map.of("state", "AVAILABLE");
        } catch (Throwable ignored) { return Map.of("state", "UNAVAILABLE", "reason", "runtime-missing"); }
    }

    private static String processId() {
        try {
            return ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
