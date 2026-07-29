package com.madlava.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigurationManagerTest {
    @Test void versionsAreMonotonicAndInvalidReloadKeepsPrevious(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        long first=manager.current().version();
        RuntimeConfigurationManager.UpdateResult applied=manager.reload(
                Map.of("enabled",false),Map.of(),"test-2");
        assertTrue(applied.applied()); assertEquals(first+1,manager.current().version());
        long validVersion=manager.current().version();
        RuntimeConfigurationManager.UpdateResult rejected=manager.reload(
                Map.of("unknown.property",true),Map.of(),"test-3");
        assertFalse(rejected.applied()); assertEquals(validVersion,manager.current().version());
        assertEquals(false,manager.current().values().get("enabled"));
    }
    @Test void listenerReceivesAtomicReplacementAndDiffRedactsSecrets(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        RuntimeConfigurationManager.ConfigurationState[] seen=new RuntimeConfigurationManager.ConfigurationState[1];
        manager.addListener((before,after)->seen[0]=after);
        RuntimeConfigurationManager.ConfigurationState before=manager.current();
        RuntimeConfigurationManager.UpdateResult result=manager.reload(
                Map.of("enabled",false,"security.token","new-secret"),Map.of(),"test-2");
        assertTrue(result.applied()); assertSame(result.state(),seen[0]);
        assertEquals("<redacted>",ConfigurationDiff.between(
                before,manager.current()).getOrDefault("security.token",Map.of()).get("to"));
    }
    @Test void nestedJsonReloadFlattensReportingAndFilters() throws Exception {
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        java.nio.file.Path file=Files.createTempFile("madlava-config", ".json");
        Files.writeString(file,"{\"reporting\":{\"output\":\"trace-b\",\"intervalMillis\":2000},\"filters\":{\"methods\":{\"includes\":[\"a.B.c\",\"x.Y.z(*)\"]}}}");
        RuntimeConfigurationManager.UpdateResult result=manager.reloadJson(file,Map.of());
        assertTrue(result.applied()); assertEquals("trace-b",manager.current().values().get("reporting.output"));
        assertEquals("a.B.c;x.Y.z(*)",manager.current().values().get("filters.methods.includes"));
        Files.deleteIfExists(file);
    }
}
