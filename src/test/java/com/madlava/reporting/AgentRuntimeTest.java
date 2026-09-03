package com.madlava.reporting;

import com.madlava.config.AgentOptions;
import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.RuntimeConfigurationManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeTest {
    @Test
    void runtimeFeatureSwitchesControlJvmCollection() {
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),
                Map.of("features.heapUsage.enabled", false), "test");
        AgentRuntime runtime = new AgentRuntime("test", "hash", AgentOptions.parse("diagnosticsToStderr=false"),
                null, null, null, configuration, false);

        Map<?,?> first = (Map<?,?>) runtime.snapshot("test").get("cumulative");
        assertFalse(first.containsKey("heapUsage"));
        assertTrue(first.containsKey("nonHeapUsage"));

        assertTrue(configuration.reload(Map.of("features.heapUsage.enabled", true), Map.of(), "test").applied());
        Map<?,?> second = (Map<?,?>) runtime.snapshot("test").get("cumulative");
        assertTrue(second.containsKey("heapUsage"));
    }

    @Test void shutdownSnapshotCarriesExplicitFinalMarker() {
        AgentRuntime runtime=new AgentRuntime("test","hash",AgentOptions.parse(""),null,null,null);
        assertEquals(false,runtime.snapshot("periodic").get("final"));
        assertEquals(true,runtime.snapshot("shutdown").get("final"));
    }
    @Test void primaryConfigurationHashTracksThePinnedRuntimeGeneration() {
        RuntimeConfigurationManager configuration=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        AgentRuntime runtime=new AgentRuntime("test","startup-hash",AgentOptions.parse(""),null,null,null,configuration,false);
        Map<String,Object> first=runtime.snapshot("test");
        assertEquals("startup-hash",first.get("startupConfigurationHash"));
        assertEquals(configuration.current().hash(),first.get("configurationHash"));
        String before=String.valueOf(first.get("configurationHash"));
        assertTrue(configuration.reload(Map.of("features.heapUsage.enabled",false),Map.of(),"test-2").applied());
        Map<String,Object> second=runtime.snapshot("test");
        assertNotEquals(before,second.get("configurationHash"));
        assertEquals(configuration.current().hash(),second.get("configurationHash"));
        assertEquals("startup-hash",second.get("startupConfigurationHash"));
    }

}
