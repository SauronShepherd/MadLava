package com.madlava.agent;

import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.RuntimeConfigurationManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MadLavaAgentTest {
    @Test void legacyNoOpSettingsAreReportedAsUnsupportedWhenNonDefault() {
        ConfigurationResolver resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(resolver, Map.of("security.token","secret"), "test");
        assertEquals("security.token", MadLavaAgent.unsupportedLegacyConfiguration(manager.current()));

        manager=new RuntimeConfigurationManager(resolver, Map.of("safety.featureSnapshotTimeoutMillis",2000), "test");
        assertEquals("safety.featureSnapshotTimeoutMillis", MadLavaAgent.unsupportedLegacyConfiguration(manager.current()));
    }

    @Test void methodRuleHotReloadRequiresRetransformationSupport() {
        assertTrue(MadLavaAgent.liveMethodRuleReloadSupported(true, true));
        assertFalse(MadLavaAgent.liveMethodRuleReloadSupported(true, false));
        assertFalse(MadLavaAgent.liveMethodRuleReloadSupported(false, true));
    }
    @Test void startupConfigurationCanonicalizationIsLengthFramed() {
        Map<String,String> first=new java.util.LinkedHashMap<>(); first.put("a","x\nb=y"); first.put("b","z");
        Map<String,String> second=new java.util.LinkedHashMap<>(); second.put("a","x"); second.put("b","y\nb=z");
        assertNotEquals(MadLavaAgent.canonical(first),MadLavaAgent.canonical(second));
    }

}
