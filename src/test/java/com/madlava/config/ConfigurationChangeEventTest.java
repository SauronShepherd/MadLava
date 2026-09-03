package com.madlava.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationChangeEventTest {
    @Test void acceptedEventContainsVersionsAndDiff() {
        RuntimeConfigurationManager manager = new RuntimeConfigurationManager(new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        RuntimeConfigurationManager.ConfigurationState before = manager.current();
        RuntimeConfigurationManager.ConfigurationState after = manager.reload(Map.of("enabled", false), Map.of(), "test").state();
        Map<String,Object> event = ConfigurationChangeEvent.accepted(before, after);
        assertEquals("configuration-change", event.get("type"));
        assertEquals(before.version(), event.get("previousConfigurationVersion"));
        assertTrue(((Map<?,?>) event.get("changes")).containsKey("enabled"));
    }
}
