package com.madlava.config;

import java.time.Instant;
import java.util.*;

/** Stable telemetry payload for an accepted or rejected configuration update. */
public final class ConfigurationChangeEvent {
    private ConfigurationChangeEvent() { }
    public static Map<String, Object> accepted(
            RuntimeConfigurationManager.ConfigurationState previous,
            RuntimeConfigurationManager.ConfigurationState current) {
        Map<String, Object> event = base("configuration-change", current.version());
        event.put("previousConfigurationVersion", previous.version());
        event.put("changes", ConfigurationDiff.between(previous, current));
        return Collections.unmodifiableMap(event);
    }
    public static Map<String, Object> rejected(RuntimeConfigurationManager.ConfigurationState current, String reason) {
        Map<String, Object> event = base("configuration-change-rejected", current.version());
        event.put("reason", reason == null ? "INVALID_CONFIGURATION" : reason);
        return Collections.unmodifiableMap(event);
    }
    private static Map<String, Object> base(String type, long version) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("recordType", "configuration-change");
        event.put("type", type); event.put("timestamp", Instant.now().toString()); event.put("configurationVersion", version);
        return event;
    }
}
