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
import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.ConfigurationWatcher;
import com.madlava.config.RuntimeConfigurationManager;
import com.madlava.methods.MethodObservationPlan;
import com.madlava.methods.MethodRuleList;
import com.madlava.api.MadLavaRuntimeRegistry;

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
        ConfigurationWatcher configurationWatcher = null;
        RuntimeConfigurationManager runtimeConfiguration = null;
        AgentRuntime runtime = null;
        boolean methodBridgeConfigured = false;
        boolean serializationBridgeConfigured = false;
        try {
            AgentOptions options = AgentOptions.parse(rawArguments);
            runtimeConfiguration = new RuntimeConfigurationManager(
                    new ConfigurationResolver(ConfigurationMetadata.baseline()),
                    java.util.Collections.emptyMap(), options.configurationSourcePath(), options.runtimeConfigurationOverrides());
            if (!options.configurationSourcePath().isBlank()) {
                RuntimeConfigurationManager.UpdateResult initialReload = runtimeConfiguration.reloadJson(
                        java.nio.file.Paths.get(options.configurationSourcePath()), java.util.Collections.emptyMap());
                if (!initialReload.applied()) {
                    throw new IllegalArgumentException(
                            "Invalid MadLava runtime configuration: " + initialReload.reason());
                }
            }
            String unsupportedStartupKey = unsupportedLegacyConfiguration(runtimeConfiguration.current());
            if (unsupportedStartupKey != null) {
                throw new IllegalArgumentException("Unsupported MadLava configuration property: " + unsupportedStartupKey);
            }
            String version = MadLavaAgent.class.getPackage().getImplementationVersion();
            if (version == null || version.isBlank()) {
                version = FALLBACK_VERSION;
            }

            MethodFilter methodFilter = MethodFilter.parse(
                    options.methodIncludes(),
                    options.methodExcludes());
            boolean methodCallbacksEnabled = options.methodProfilingEnabled() || options.methodTracingEnabled();
            MethodRegistry methodRegistry = methodCallbacksEnabled
                    ? new MethodRegistry(options.methodMaxEntries())
                    : null;
            MethodMetrics methodMetrics = methodRegistry == null
                    ? null
                    : new MethodMetrics(methodRegistry, options.methodArgumentMaxGroups());
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

            if (options.methodProfilingEnabled() || options.methodTracingEnabled() || options.sparkSerializationEnabled()) {
                transformer = new MadLavaTransformer(
                        methodCallbacksEnabled,
                        methodFilter,
                        methodRegistry == null ? new MethodRegistry(1) : methodRegistry,
                        options.sparkSerializationEnabled(),
                        serializationPlan == null
                                ? new SparkSerializationPlan(options.sparkSerializationProfile())
                                : serializationPlan,
                        MethodObservationPlan.compile(MethodRuleList.split(options.methodIncludes())));
                instrumentation.addTransformer(
                        transformer,
                        instrumentation.isRetransformClassesSupported());
            }

            final boolean liveMethodRules = liveMethodRuleReloadSupported(methodCallbacksEnabled, instrumentation.isRetransformClassesSupported());
            final boolean liveTracing = methodMetrics != null;
            runtimeConfiguration.addTransitionValidator((previous, proposed) -> {
                String unsupportedKey = unsupportedLegacyConfiguration(proposed);
                if (unsupportedKey != null) return "UNSUPPORTED_CONFIGURATION: " + unsupportedKey;
                if (liveMethodRules && (!java.util.Objects.equals(
                        previous.values().get("filters.methods.includes"), proposed.values().get("filters.methods.includes"))
                        || !java.util.Objects.equals(
                        previous.values().get("filters.methods.excludes"), proposed.values().get("filters.methods.excludes")))) {
                    try {
                        String includes = String.valueOf(proposed.values().get("filters.methods.includes"));
                        String excludes = String.valueOf(proposed.values().get("filters.methods.excludes"));
                        MethodFilter.parse(includes, excludes);
                        MethodObservationPlan.compile(MethodRuleList.split(includes));
                    } catch (RuntimeException invalidRule) {
                        return "INVALID_METHOD_FILTER";
                    }
                }
                java.util.Set<String> liveKeys = new java.util.HashSet<>();
                liveKeys.add("output.directory");
                liveKeys.add("reporting.human.maxRows");
                liveKeys.add("reporting.human.truncate");
                for (String section : new String[]{"methodProfiling", "argumentGroups", "sparkSerialization", "sparkSerializationDetail", "diagnostics"})
                    liveKeys.add("reporting.human.sections." + section + ".maxRows");
                if (liveMethodRules) {
                    liveKeys.add("filters.methods.includes");
                    liveKeys.add("filters.methods.excludes");
                }
                if (liveTracing) {
                    liveKeys.add("features.methodTracing.enabled");
                    liveKeys.add("features.methodTracing.sampleRate");
                }
                for (String feature : new String[]{"heapUsage", "nonHeapUsage", "bufferPools", "garbageCollection",
                        "threadStatistics", "threadCpu", "processResources", "classLoaderInsights",
                        "jvmExecutionEngine", "selfObservability"}) {
                    liveKeys.add("features." + feature + ".enabled");
                }
                for (String key : proposed.values().keySet()) {
                    if (java.util.Objects.equals(previous.values().get(key), proposed.values().get(key))) continue;
                    if (!liveKeys.contains(key)) return "RESTART_REQUIRED: " + key;
                }
                return null;
            });

            runtime = new AgentRuntime(
                    version,
                    sha256(canonical(options.effectiveMap())),
                    options,
                    methodMetrics,
                    serializationMetrics,
                    serializationPlan,
                    runtimeConfiguration,
                    instrumentation.isRetransformClassesSupported());
            if (!MadLavaRuntimeRegistry.register(runtime)) {
                throw new IllegalStateException("MadLava runtime already registered");
            }
            MadLavaRuntimeRegistry.registerConfiguration(runtimeConfiguration);
            reporter = new JsonlReporter(runtime, options.outputDirectory());
            reporter.bindConfiguration(runtimeConfiguration);
            reporter.start(options.snapshotIntervalSeconds(), options.shutdownSnapshotOnly());
            if (options.methodTracingEnabled() && methodMetrics != null) {
                methodMetrics.enableTracing(runtimeConfiguration.current().version(), options.methodTracingSampleRate(), reporter::submitTraceEvent);
            }
            if (transformer != null) {
                MadLavaTransformer liveTransformer = transformer;
                JsonlReporter tracingReporter = reporter;
                runtimeConfiguration.addListener((previous, current) -> {
                    Object tracing = current.values().get("features.methodTracing.enabled");
                    if (methodMetrics != null && tracing != null) {
                        if (Boolean.parseBoolean(String.valueOf(tracing))) {
                            Object sampleRate = current.values().get("features.methodTracing.sampleRate");
                            double rate = sampleRate instanceof Number ? ((Number) sampleRate).doubleValue() : 1.0;
                            methodMetrics.enableTracing(current.version(), rate, tracingReporter::submitTraceEvent);
                        } else {
                            methodMetrics.disableTracing();
                        }
                    }
                    Object includes = current.values().get("filters.methods.includes");
                    Object excludes = current.values().get("filters.methods.excludes");
                    Object previousIncludes = previous.values().get("filters.methods.includes");
                    Object previousExcludes = previous.values().get("filters.methods.excludes");
                    if (!java.util.Objects.equals(includes, previousIncludes)
                            || !java.util.Objects.equals(excludes, previousExcludes)) {
                        MethodFilter previousFilter = MethodFilter.parse(
                                String.valueOf(previousIncludes), String.valueOf(previousExcludes));
                        MethodFilter nextFilter = MethodFilter.parse(String.valueOf(includes), String.valueOf(excludes));
                        liveTransformer.updateMethodSelection(
                                nextFilter,
                                MethodObservationPlan.compile(MethodRuleList.split(String.valueOf(includes))));
                        int failures = retransformAlreadyLoaded(instrumentation, liveTransformer, previousFilter);
                        if (failures > 0) {
                            throw new IllegalStateException(
                                    "MadLava method-filter reload left " + failures
                                            + " loaded class(es) untransformed");
                        }
                    }
                });
            }

            if (options.hotReloadEnabled() && !options.configurationSourcePath().isBlank()) {
                configurationWatcher = new ConfigurationWatcher(
                        java.nio.file.Paths.get(options.configurationSourcePath()), runtimeConfiguration);
                configurationWatcher.start();
            }

            MadLavaTransformer activeTransformer = transformer;
            if (retransformLoadedClasses && activeTransformer != null) {
                retransformAlreadyLoaded(instrumentation, activeTransformer);
            }

            ConfigurationWatcher activeWatcher = configurationWatcher;
            JsonlReporter activeReporter = reporter;
            RuntimeConfigurationManager activeConfiguration = runtimeConfiguration;
            AgentRuntime activeRuntime = runtime;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (activeWatcher != null) activeWatcher.close();
                activeReporter.close();
                MadLavaRuntimeRegistry.clear(activeRuntime);
                MadLavaRuntimeRegistry.clearConfiguration(activeConfiguration);
            }, "madlava-shutdown"));
            if (options.diagnosticsToStderr()) {
                System.err.println(
                        "MadLava " + version
                                + " Iteration-12 ready; report="
                                + reporter.reportPath().toAbsolutePath().normalize());
            }
        } catch (Throwable failure) {
            if (configurationWatcher != null) {
                try { configurationWatcher.close(); } catch (Throwable ignored) { }
            }
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
            if (runtime != null) {
                try { MadLavaRuntimeRegistry.clear(runtime); } catch (Throwable ignored) { }
            }
            if (runtimeConfiguration != null) {
                try { MadLavaRuntimeRegistry.clearConfiguration(runtimeConfiguration); } catch (Throwable ignored) { }
            }
            STARTED.set(false);
            System.err.println(
                    "MadLava Iteration-12 bootstrap disabled: "
                            + failure.getClass().getSimpleName());
        }
    }

    static String canonical(Map<String, String> values) {
        StringBuilder output = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            // Length framing makes the representation injective even when values contain '=',
            // newlines, or text that resembles a following configuration key.
            output.append(key.length()).append(':').append(key)
                    .append(value.length()).append(':').append(value);
        }
        return output.toString();
    }

    private static int retransformAlreadyLoaded(
            Instrumentation instrumentation,
            MadLavaTransformer transformer) {
        return retransformAlreadyLoaded(instrumentation, transformer, null);
    }

    /**
     * Retransforms classes covered by either the current transformer rules or the previous
     * method filter. The previous filter is required when rules are narrowed/removed: a class
     * that only matched the old filter must still be retransformed once so its old probes are
     * removed from the JVM's retransformed definition.
     */
    private static int retransformAlreadyLoaded(
            Instrumentation instrumentation,
            MadLavaTransformer transformer,
            MethodFilter previousMethodFilter) {
        if (!instrumentation.isRetransformClassesSupported()) {
            return 0;
        }
        int failures = 0;
        for (Class<?> candidate : instrumentation.getAllLoadedClasses()) {
            try {
                if (candidate == null || !instrumentation.isModifiableClass(candidate)) {
                    continue;
                }
                String internalName = candidate.getName().replace('.', '/');
                String owner = candidate.getName();
                boolean matchedPreviously = previousMethodFilter != null
                        && previousMethodFilter.mayMatchClass(owner);
                if (matchedPreviously || transformer.mayTransformClass(internalName)) {
                    instrumentation.retransformClasses(candidate);
                }
            } catch (Throwable ignored) {
                // A single class must never disable the agent or the application, but reload
                // callers need to know that the JVM is now only partially retransformed.
                failures++;
            }
        }
        return failures;
    }

    static boolean liveMethodRuleReloadSupported(boolean methodCallbacksEnabled, boolean retransformationSupported) {
        return methodCallbacksEnabled && retransformationSupported;
    }

    /** Legacy metadata retained for schema/history compatibility but not implemented by the active agent path. */
    static String unsupportedLegacyConfiguration(RuntimeConfigurationManager.ConfigurationState state) {
        if (state == null) return null;
        ConfigurationMetadata metadata = ConfigurationMetadata.baseline();
        for (String key : java.util.List.of(
                "enabled", "reporting.output", "reporting.enabled", "reporting.intervalMillis",
                "configuration.strict", "configuration.reload.enabled", "configuration.reload.intervalSeconds",
                "reporting.human.enabled", "security.token", "safety.maxFeatureErrors",
                "safety.featureSnapshotTimeoutMillis", "safety.globalSnapshotTimeoutMillis")) {
            ConfigurationMetadata.Entry entry = metadata.entries().get(key);
            if (entry != null && !java.util.Objects.equals(state.values().get(key), entry.defaultValue())) return key;
        }
        return null;
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
