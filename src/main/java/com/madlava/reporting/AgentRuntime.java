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

    public AgentRuntime(
            String version,
            String configurationHash,
            AgentOptions options,
            MethodMetrics methodMetrics,
            SparkSerializationMetrics serializationMetrics,
            SparkSerializationPlan serializationPlan) {
        this(version, configurationHash, options, methodMetrics, serializationMetrics, serializationPlan, null);
    }

    public AgentRuntime(
            String version, String configurationHash, AgentOptions options, MethodMetrics methodMetrics,
            SparkSerializationMetrics serializationMetrics, SparkSerializationPlan serializationPlan,
            RuntimeConfigurationManager runtimeConfiguration) {
        this.version = version;
        this.configurationHash = configurationHash;
        this.options = options;
        this.methodMetrics = methodMetrics;
        this.serializationMetrics = serializationMetrics;
        this.serializationPlan = serializationPlan;
        this.runtimeConfiguration = runtimeConfiguration;
        this.startedEpochMillis = System.currentTimeMillis();
    }

    public Map<String, Object> snapshot(String reason) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("timestamp", Instant.now().toString());
        root.put("reason", reason);
        root.put("agentVersion", version);
        root.put("configurationHash", configurationHash);
        if (runtimeConfiguration != null) {
            RuntimeConfigurationManager.ConfigurationState state = runtimeConfiguration.current();
            root.put("configurationVersion", state.version());
            root.put("runtimeConfigurationHash", state.hash());
            root.put("configurationRuntime", Map.of(
                    "version", state.version(),
                    "successfulReloads", runtimeConfiguration.successfulReloads(),
                    "failedReloads", runtimeConfiguration.failedReloads(),
                    "lastReloadEpochMillis", runtimeConfiguration.lastReloadEpochMillis()));
        }
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("source", options.configurationSource());
        provenance.put("sourcePath", options.configurationSourcePath());
        provenance.put("hashAlgorithm", "SHA-256");
        root.put("configuration", provenance);
        Map<String, Object> capabilities = new LinkedHashMap<>();
        RuntimeCapabilities.detect().values().forEach((name, available) ->
                capabilities.put(name, Map.of("state", available ? "AVAILABLE" : "UNAVAILABLE")));
        capabilities.put("jfr", capabilityJfr());
        capabilities.put("bootstrapBridge", Map.of("state", "UNAVAILABLE", "reason", "not-installed-in-0.1.0"));
        capabilities.put("retransformation", Map.of("state", "AVAILABLE", "reason", "agent-capability"));
        root.put("capabilities", capabilities);
        root.put("sparkRuntime", SparkRuntimeInfo.detect());
        Map<String, Object> cumulative = new LinkedHashMap<>(jvmMetrics.collect());
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
        RuntimeObservationBridge.Snapshot observations = RuntimeObservationBridge.snapshot();
        cumulative.put("io", Map.of(
                "observedLayers", observations.ioReport(),
                "source", "RuntimeObservationBridge",
                "payloadCapture", false));
        cumulative.put("serialization", Map.of(
                "observedLayers", observations.serializationReport(),
                "source", "RuntimeObservationBridge",
                "payloadCapture", false));
        cumulative.put("threadPools", ObservedExecutorService.snapshot().report());
        cumulative.put("diagnostics", DiagnosticsRuntime.snapshot());
        cumulative.put("spark", SparkObservationRegistry.snapshot(Thread.currentThread().getContextClassLoader()));
        root.put("cumulative", cumulative);
        root.put("pid", processId());
        root.put("uptimeMillis", Math.max(0L, System.currentTimeMillis() - startedEpochMillis));

        Map<String, Object> featureReports = new LinkedHashMap<>();
        if (methodMetrics != null) {
            featureReports.put("methodProfiling", methodMetrics.report());
        } else {
            featureReports.put("methodProfiling", Map.of("state", "DISABLED"));
        }
        if (serializationMetrics != null && serializationPlan != null) {
            featureReports.put("sparkSerialization", serializationMetrics.report(serializationPlan));
        } else {
            featureReports.put("sparkSerialization", Map.of("state", "DISABLED"));
        }
        root.put("features", featureReports);

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("methodProfiling", options.methodProfilingEnabled());
        configuration.put("methodIncludes", options.methodIncludes());
        configuration.put("methodExcludes", options.methodExcludes());
        configuration.put("methodMaxEntries", options.methodMaxEntries());
        configuration.put("sparkSerialization", options.sparkSerializationEnabled());
        configuration.put("sparkSerializationProfile", options.sparkSerializationProfile().name());
        configuration.put("sparkSerializationRootClasses", options.sparkSerializationRootClasses());
        configuration.put("sparkSerializationMaxGroups", options.sparkSerializationMaxGroups());
        configuration.put("reportMaxRows", options.reportMaxRows());
        configuration.put("reportTruncate", options.reportTruncate());
        for (String section : new String[]{"methodProfiling", "argumentGroups", "sparkSerialization", "sparkSerializationDetail", "diagnostics"})
            configuration.put("reportMaxRows." + section, options.reportSectionMaxRows(section));
        root.put("effectiveConfiguration", configuration);
        return root;
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
