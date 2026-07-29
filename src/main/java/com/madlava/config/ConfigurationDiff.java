package com.madlava.config;

import java.util.*;

/** Bounded, secret-free diff for configuration-change telemetry. */
public final class ConfigurationDiff {
    private ConfigurationDiff() { }
    public static Map<String, Map<String, Object>> between(
            RuntimeConfigurationManager.ConfigurationState before,
            RuntimeConfigurationManager.ConfigurationState after) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Set<String> keys = new TreeSet<>(); keys.addAll(before.values().keySet()); keys.addAll(after.values().keySet());
        for (String key : keys) {
            Object oldValue = before.values().get(key), newValue = after.values().get(key);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", safe(key, oldValue)); change.put("to", safe(key, newValue)); result.put(key, change);
            }
        }
        return Collections.unmodifiableMap(result);
    }
    private static Object safe(String key, Object value) {
        if (key.toLowerCase(Locale.ROOT).contains("password") || key.toLowerCase(Locale.ROOT).contains("secret")
                || key.toLowerCase(Locale.ROOT).contains("token") || key.toLowerCase(Locale.ROOT).contains("authorization")) return "<redacted>";
        return value;
    }
}
