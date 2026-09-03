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
    public static final int DEFAULT_REPORT_MAX_ROWS = 50;

    private final Map<String, String> values;
    private final String configurationSource;
    private final String configurationSourcePath;
    private final Map<String, String> explicitValues;

    private AgentOptions(Map<String, String> values, String configurationSource, String configurationSourcePath,
                         Map<String, String> explicitValues) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.configurationSource = configurationSource;
        this.configurationSourcePath = configurationSourcePath;
        this.explicitValues = Collections.unmodifiableMap(new LinkedHashMap<>(explicitValues));
    }

    public static AgentOptions parse(String raw) {
        Map<String, String> parsed = parseCompactArguments(raw);
        validateCompactKeys(parsed);
        Map<String, String> explicitValues = new LinkedHashMap<>(parsed);
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
        AgentOptions options = new AgentOptions(parsed, source, sourcePath, explicitValues);
        options.validateTypedValues();
        return options;
    }


    /** Quote-aware compact parser. Commas inside double quotes are part of the value. */

    private static void validateCompactKeys(Map<String, String> parsed) {
        java.util.Set<String> known = java.util.Set.of(
                "config", "output", "snapshotIntervalSeconds", "shutdownSnapshotOnly", "hotReload",
                "diagnosticsToStderr", "methodProfiling", "methodTracing", "methodTracingSampleRate",
                "methodMaxEntries", "methodArgumentMaxGroups", "methodInclude", "methodExclude",
                "reportMaxRows", "reportTruncate", "sparkSerialization", "sparkSerializationProfile",
                "sparkSerializationRootClasses", "sparkSerializationMaxGroups");
        java.util.Set<String> reportSections = java.util.Set.of(
                "methodProfiling", "argumentGroups", "sparkSerialization", "sparkSerializationDetail", "diagnostics");
        for (String key : parsed.keySet()) {
            if (known.contains(key)) continue;
            if (key.startsWith("reportMaxRows.") && reportSections.contains(key.substring("reportMaxRows.".length()))) continue;
            throw new IllegalArgumentException("Unknown MadLava agent option: " + key);
        }
    }

    private static Map<String, String> parseCompactArguments(String raw) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return parsed;
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i <= raw.length(); i++) {
            char current = i == raw.length() ? ',' : raw.charAt(i);
            if (current == '"') {
                quoted = !quoted;
            } else if (current == ',' && !quoted) {
                addCompactArgument(parsed, token.toString());
                token.setLength(0);
            } else {
                token.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted agent argument");
        return parsed;
    }

    private static void addCompactArgument(Map<String, String> parsed, String token) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) return;
        int separator = trimmed.indexOf('=');
        String key;
        String value;
        if (separator < 0) {
            key = trimmed;
            value = "true";
        } else {
            key = trimmed.substring(0, separator).trim();
            value = trimmed.substring(separator + 1).trim();
        }
        if (key.isEmpty()) throw new IllegalArgumentException("Empty agent argument name");
        if (parsed.putIfAbsent(key, value) != null)
            throw new IllegalArgumentException("Duplicate agent argument: " + key);
    }

    private static Path discoverConfiguration() {
        ClassLoader loader = AgentOptions.class.getClassLoader();
        if (loader != null) {
            try {
                java.net.URL resource = loader.getResource("madlava.json");
                // Path-based loading only supports file: resources. A jar: resource must not
                // prevent the documented working-directory fallback from being checked.
                if (resource != null && "file".equalsIgnoreCase(resource.getProtocol())) {
                    Path classpath = Paths.get(resource.toURI());
                    if (Files.isRegularFile(classpath)) return classpath;
                }
            } catch (Exception ignored) {
                // Continue with the working-directory fallback.
            }
        }
        try {
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

    public boolean methodTracingEnabled() { return booleanValue("methodTracing", false); }
    public double methodTracingSampleRate() { try { double parsed=Double.parseDouble(value("methodTracingSampleRate", "1")); if(!Double.isFinite(parsed)||parsed<0||parsed>1)throw new IllegalArgumentException("Invalid methodTracingSampleRate");return parsed; } catch (NumberFormatException failure) { throw new IllegalArgumentException("Invalid methodTracingSampleRate",failure); } }

    public String methodIncludes() {
        return value("methodInclude", "");
    }

    public String methodExcludes() {
        return value("methodExclude", "");
    }

    public int methodMaxEntries() {
        return positiveInt("methodMaxEntries", DEFAULT_METHOD_MAX_ENTRIES);
    }
    public int methodArgumentMaxGroups() { return positiveInt("methodArgumentMaxGroups", 256); }

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

    public boolean hotReloadEnabled() { return booleanValue("hotReload", false); }

    public boolean diagnosticsToStderr() {
        return booleanValue("diagnosticsToStderr", true);
    }
    public int reportMaxRows() { return nonNegativeInt("reportMaxRows", DEFAULT_REPORT_MAX_ROWS); }
    public int reportTruncate() { return nonNegativeInt("reportTruncate", 100); }
    public int reportSectionMaxRows(String section) { return nonNegativeInt("reportMaxRows." + section, reportMaxRows()); }

    public String value(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public Map<String, String> asMap() {
        return values;
    }

    public String configurationSource() { return configurationSource; }
    public String configurationSourcePath() { return configurationSourcePath; }

    /** Canonical runtime keys supplied explicitly in the compact -javaagent argument string. */
    public Map<String, String> runtimeConfigurationOverrides() {
        Map<String, String> result = new LinkedHashMap<>();
        explicitValues.forEach((key, value) -> {
            String canonical = runtimeKey(key);
            if (canonical != null) result.put(canonical, value);
        });
        return Collections.unmodifiableMap(result);
    }

    private static String runtimeKey(String key) {
        switch (key) {
            case "output": return "output.directory";
            case "snapshotIntervalSeconds": return "reporting.snapshotIntervalSeconds";
            case "shutdownSnapshotOnly": return "reporting.shutdownSnapshotOnly";
            case "hotReload": return "configuration.hotReload.enabled";
            case "reportMaxRows": return "reporting.human.maxRows";
            case "reportTruncate": return "reporting.human.truncate";
            case "methodProfiling": return "features.methodProfiling.enabled";
            case "methodTracing": return "features.methodTracing.enabled";
            case "methodTracingSampleRate": return "features.methodTracing.sampleRate";
            case "methodMaxEntries": return "features.methodProfiling.maxEntries";
            case "methodArgumentMaxGroups": return "features.methodProfiling.argumentGrouping.maxGroupsPerMethod";
            case "methodInclude": return "filters.methods.includes";
            case "methodExclude": return "filters.methods.excludes";
            case "sparkSerialization": return "features.sparkSerialization.enabled";
            case "sparkSerializationProfile": return "features.sparkSerialization.profile";
            case "sparkSerializationRootClasses": return "features.sparkSerialization.rootClasses";
            case "sparkSerializationMaxGroups": return "features.sparkSerialization.maxGroups";
            default:
                if (key.startsWith("reportMaxRows."))
                    return "reporting.human.sections." + key.substring("reportMaxRows.".length()) + ".maxRows";
                return null;
        }
    }

    /** Fully materialized runtime configuration used for semantic identity. */
    public Map<String, String> effectiveMap() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("output", outputDirectory().toString());
        result.put("snapshotIntervalSeconds", Integer.toString(snapshotIntervalSeconds()));
        result.put("shutdownSnapshotOnly", Boolean.toString(shutdownSnapshotOnly()));
        result.put("diagnosticsToStderr", Boolean.toString(diagnosticsToStderr()));
        result.put("methodProfiling", Boolean.toString(methodProfilingEnabled()));
        result.put("methodTracing", Boolean.toString(methodTracingEnabled()));
        result.put("methodTracingSampleRate", Double.toString(methodTracingSampleRate()));
        result.put("methodInclude", methodIncludes());
        result.put("methodExclude", methodExcludes());
        result.put("methodMaxEntries", Integer.toString(methodMaxEntries()));
        result.put("methodArgumentMaxGroups", Integer.toString(methodArgumentMaxGroups()));
        result.put("hotReload", Boolean.toString(hotReloadEnabled()));
        result.put("reportMaxRows", Integer.toString(reportMaxRows()));
        result.put("reportTruncate", Integer.toString(reportTruncate()));
        for (String section : new String[]{"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"})
            result.put("reportMaxRows." + section, Integer.toString(reportSectionMaxRows(section)));
        result.put("sparkSerialization", Boolean.toString(sparkSerializationEnabled()));
        result.put("sparkSerializationProfile", sparkSerializationProfile().name());
        result.put("sparkSerializationRootClasses", Boolean.toString(sparkSerializationRootClasses()));
        result.put("sparkSerializationMaxGroups", Integer.toString(sparkSerializationMaxGroups()));
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readConfiguration(Path path) {
        try {
            Object rootValue = SimpleJsonParser.parse(ConfigurationFileReader.read(path));
            if (!(rootValue instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("MadLava configuration root must be an object");
            }
            Map<String, Object> root = (Map<String, Object>) rootValue;
            // Validate the complete file schema before cherry-picking startup settings. Without
            // this, typos/unknown properties were silently ignored by AgentOptions even though
            // the same file was rejected by RuntimeConfigurationManager during hot reload.
            Map<String, Object> flattened = new LinkedHashMap<>();
            RuntimeConfigurationManager.flatten("", root, flattened);
            new ConfigurationResolver(ConfigurationMetadata.baseline()).resolve(
                    flattened, Collections.emptyMap(), path.toAbsolutePath().normalize().toString());

            Map<String, String> values = new LinkedHashMap<>();

            put(values, "output", path(root, "output", "directory"));
            putPositiveInt(values, "snapshotIntervalSeconds", path(root, "reporting", "snapshotIntervalSeconds"));
            putBoolean(values, "shutdownSnapshotOnly", path(root, "reporting", "shutdownSnapshotOnly"));
            putBoolean(values, "hotReload", path(root, "configuration", "hotReload", "enabled"));
            putNonNegativeInt(values, "reportMaxRows", path(root, "reporting", "human", "maxRows"));
            putNonNegativeInt(values, "reportTruncate", path(root, "reporting", "human", "truncate"));
            String[] reportSections={"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"};
            for(String section:reportSections)putNonNegativeInt(values,"reportMaxRows."+section,path(root,"reporting","human","sections",section,"maxRows"));

            putBoolean(values, "methodProfiling", path(root, "features", "methodProfiling", "enabled"));
            putBoolean(values, "methodTracing", path(root, "features", "methodTracing", "enabled"));
            put(values, "methodTracingSampleRate", path(root, "features", "methodTracing", "sampleRate"));
            putPositiveInt(values, "methodMaxEntries", path(root, "features", "methodProfiling", "maxEntries"));
            putPositiveInt(values, "methodArgumentMaxGroups", path(root, "features", "methodProfiling", "argumentGrouping", "maxGroupsPerMethod"));
            put(values, "methodInclude", joined(path(root, "filters", "methods", "includes")));
            put(values, "methodExclude", joined(path(root, "filters", "methods", "excludes")));

            Object serialization = path(root, "features", "sparkSerialization");
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
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Method filter entries must be strings");
            }
            if (output.length() > 0) {
                output.append(';');
            }
            output.append((String)item);
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
        if (!isIntegralNumber(value)) {
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected positive integer");
        }
        long parsed=((Number)value).longValue();
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected positive integer");
        }
        values.put(key, Long.toString(parsed));
    }
    private static void putNonNegativeInt(Map<String, String> values, String key, Object value) {
        if (value == null) return;
        if (!isIntegralNumber(value))
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected non-negative integer");
        long parsed=((Number)value).longValue();
        if (parsed < 0 || parsed > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid configuration: " + key + " expected non-negative integer");
        values.put(key, Long.toString(parsed));
    }
    private static boolean isIntegralNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = values.get(key);
        if(value==null||value.isBlank())return fallback;
        if(!"true".equalsIgnoreCase(value)&&!"false".equalsIgnoreCase(value))
            throw new IllegalArgumentException("Invalid boolean agent option: "+key);
        return Boolean.parseBoolean(value);
    }

    private int positiveInt(String key, int fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) return fallback;
        try { int parsed=Integer.parseInt(value);if(parsed<=0)throw new IllegalArgumentException("Invalid positive agent option: "+key);return parsed; }
        catch(NumberFormatException failure){throw new IllegalArgumentException("Invalid integer agent option: "+key,failure);}
    }
    private int nonNegativeInt(String key, int fallback) {
        String value=values.get(key);if(value==null||value.isBlank())return fallback;
        try { int parsed=Integer.parseInt(value);if(parsed<0)throw new IllegalArgumentException("Invalid non-negative agent option: "+key);return parsed; }
        catch(NumberFormatException failure){throw new IllegalArgumentException("Invalid integer agent option: "+key,failure);}
    }

    private void validateTypedValues(){
        methodProfilingEnabled();methodTracingEnabled();methodTracingSampleRate();methodMaxEntries();methodArgumentMaxGroups();
        sparkSerializationEnabled();sparkSerializationProfile();sparkSerializationRootClasses();sparkSerializationMaxGroups();
        snapshotIntervalSeconds();shutdownSnapshotOnly();hotReloadEnabled();diagnosticsToStderr();reportMaxRows();reportTruncate();
        for(String section:new String[]{"methodProfiling","argumentGroups","sparkSerialization","sparkSerializationDetail","diagnostics"})reportSectionMaxRows(section);
    }
}
