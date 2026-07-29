package com.madlava.config;

import com.madlava.serialization.SparkSerializationProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, fail-safe parser for the compact {@code -javaagent} argument string.
 *
 * <p>The argument format intentionally remains compatible with the Iteration-11
 * comma-separated form. Method patterns are separated by semicolons so commas
 * continue to delimit top-level options.</p>
 */
public final class AgentOptions {
    public static final int DEFAULT_METHOD_MAX_ENTRIES = 2_048;
    public static final int DEFAULT_SERIALIZATION_MAX_GROUPS = 2_048;
    public static final int DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 1;

    private final Map<String, String> values;
    private final String configurationSource;
    private final String configurationSourcePath;

    private AgentOptions(Map<String, String> values, String configurationSource, String configurationSourcePath) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.configurationSource = configurationSource;
        this.configurationSourcePath = configurationSourcePath;
    }

    public static AgentOptions parse(String raw) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator < 1) {
                    parsed.put(trimmed, "true");
                } else {
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (!key.isEmpty()) {
                        parsed.put(key, value);
                    }
                }
            }
        }
        String configPath = parsed.get("config");
        String source = "embedded-defaults";
        String sourcePath = "";
        if (configPath != null && !configPath.isBlank()) {
            Map<String, String> configured = readConfiguration(Paths.get(configPath));
            configured.putAll(parsed); // Explicit compact arguments always win.
            parsed = configured;
            source = "explicit-agent-option";
            sourcePath = Paths.get(configPath).toAbsolutePath().normalize().toString();
        } else {
            Path discovered = discoverConfiguration();
            if (discovered != null) {
                Map<String, String> configured = readConfiguration(discovered);
                configured.putAll(parsed);
                parsed = configured;
                source = isClasspathConfiguration(discovered) ? "classpath-resource" : "working-directory";
                sourcePath = discovered.toAbsolutePath().normalize().toString();
            }
        }
        return new AgentOptions(parsed, source, sourcePath);
    }

    private static Path discoverConfiguration() {
        try {
            ClassLoader loader = AgentOptions.class.getClassLoader();
            if (loader != null && loader.getResource("madlava.json") != null) {
                return Paths.get(loader.getResource("madlava.json").toURI());
            }
            Path current = Paths.get("madlava.json").toAbsolutePath().normalize();
            if (Files.isRegularFile(current)) return current;
        } catch (Exception ignored) {
            // Discovery is best-effort; safe defaults remain active when absent.
        }
        return null;
    }

    private static boolean isClasspathConfiguration(Path discovered) {
        try {
            ClassLoader loader = AgentOptions.class.getClassLoader();
            java.net.URL resource = loader == null ? null : loader.getResource("madlava.json");
            return resource != null && "file".equalsIgnoreCase(resource.getProtocol())
                    && Paths.get(resource.toURI()).toAbsolutePath().normalize().equals(discovered.toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return false;
        }
    }

    public Path outputDirectory() {
        String configured = value("output", "madlava-output");
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    public boolean methodProfilingEnabled() {
        return booleanValue("methodProfiling", false);
    }

    public String methodIncludes() {
        return value("methodInclude", "");
    }

    public String methodExcludes() {
        return value("methodExclude", "");
    }

    public int methodMaxEntries() {
        return positiveInt("methodMaxEntries", DEFAULT_METHOD_MAX_ENTRIES);
    }

    public boolean sparkSerializationEnabled() {
        return booleanValue("sparkSerialization", false);
    }

    public SparkSerializationProfile sparkSerializationProfile() {
        return SparkSerializationProfile.parse(value("sparkSerializationProfile", "ALL"));
    }

    public boolean sparkSerializationRootClasses() {
        return booleanValue("sparkSerializationRootClasses", true);
    }

    public int sparkSerializationMaxGroups() {
        return positiveInt("sparkSerializationMaxGroups", DEFAULT_SERIALIZATION_MAX_GROUPS);
    }

    public int snapshotIntervalSeconds() {
        return positiveInt("snapshotIntervalSeconds", DEFAULT_SNAPSHOT_INTERVAL_SECONDS);
    }

    public boolean shutdownSnapshotOnly() {
        return booleanValue("shutdownSnapshotOnly", false);
    }

    public boolean diagnosticsToStderr() {
        return booleanValue("diagnosticsToStderr", true);
    }

    public String value(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public Map<String, String> asMap() {
        return values;
    }

    public String configurationSource() { return configurationSource; }
    public String configurationSourcePath() { return configurationSourcePath; }

    /** Fully materialized runtime configuration used for semantic identity. */
    public Map<String, String> effectiveMap() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("output", outputDirectory().toString());
        result.put("snapshotIntervalSeconds", Integer.toString(snapshotIntervalSeconds()));
        result.put("shutdownSnapshotOnly", Boolean.toString(shutdownSnapshotOnly()));
        result.put("diagnosticsToStderr", Boolean.toString(diagnosticsToStderr()));
        result.put("methodProfiling", Boolean.toString(methodProfilingEnabled()));
        result.put("methodInclude", methodIncludes());
        result.put("methodExclude", methodExcludes());
        result.put("methodMaxEntries", Integer.toString(methodMaxEntries()));
        result.put("sparkSerialization", Boolean.toString(sparkSerializationEnabled()));
        result.put("sparkSerializationProfile", sparkSerializationProfile().name());
        result.put("sparkSerializationRootClasses", Boolean.toString(sparkSerializationRootClasses()));
        result.put("sparkSerializationMaxGroups", Integer.toString(sparkSerializationMaxGroups()));
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readConfiguration(Path path) {
        try {
            Object rootValue = SimpleJsonParser.parse(Files.readString(path));
            if (!(rootValue instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("MadLava configuration root must be an object");
            }
            Map<String, Object> root = (Map<String, Object>) rootValue;
            Map<String, String> values = new LinkedHashMap<>();

            put(values, "output", path(root, "output", "directory"));
            putPositiveInt(values, "snapshotIntervalSeconds", path(root, "reporting", "snapshotIntervalSeconds"));
            putBoolean(values, "shutdownSnapshotOnly", path(root, "reporting", "shutdownSnapshotOnly"));

            putBoolean(values, "methodProfiling", path(root, "features", "methodProfiling", "enabled"));
            putPositiveInt(values, "methodMaxEntries", path(root, "features", "methodProfiling", "maxEntries"));
            put(values, "methodInclude", joined(path(root, "filters", "methods", "includes")));
            put(values, "methodExclude", joined(path(root, "filters", "methods", "excludes")));

            Object serialization = path(root, "features", "sparkSerialization");
            if (serialization == null) {
                serialization = path(root, "features", "spark", "serialization");
            }
            if (serialization instanceof Map<?, ?>) {
                Map<String, Object> section = (Map<String, Object>) serialization;
                putBoolean(values, "sparkSerialization", section.get("enabled"));
                put(values, "sparkSerializationProfile", section.get("profile"));
                putBoolean(values, "sparkSerializationRootClasses", section.get("rootClasses"));
                putPositiveInt(values, "sparkSerializationMaxGroups", section.get("maxGroups"));
            }
            return values;
        } catch (Exception failure) {
            throw new IllegalArgumentException(
                    "Unable to read MadLava configuration " + path.toAbsolutePath().normalize(),
                    failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> root, String... components) {
        Object current = root;
        for (String component : components) {
            if (!(current instanceof Map<?, ?>)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(component);
        }
        return current;
    }

    private static String joined(Object value) {
        if (!(value instanceof Iterable<?>)) {
            return value == null ? null : String.valueOf(value);
        }
        StringBuilder output = new StringBuilder();
        for (Object item : (Iterable<?>) value) {
            if (item == null) {
                continue;
            }
            if (output.length() > 0) {
                output.append(';');
            }
            output.append(item);
        }
        return output.toString();
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    private static void putBoolean(Map<String, String> values, String key, Object value) {
        if (value == null) return;
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected boolean");
        }
        values.put(key, String.valueOf(value));
    }

    private static void putPositiveInt(Map<String, String> values, String key, Object value) {
        if (value == null) return;
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0
                || ((Number) value).longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected positive integer");
        }
        values.put(key, String.valueOf(((Number) value).longValue()));
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private int positiveInt(String key, int fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
