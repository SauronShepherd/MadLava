package com.madlava.config;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class ConfigurationResolverTest {
 @Test void appliesDefaultsFileAndScalarOverrideInOrder(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());var config=resolver.resolve(Map.of("configuration.reload.intervalSeconds",60),Map.of("configuration.reload.intervalSeconds","90","features.heapUsage.enabled","false"),"explicit");assertEquals(90,config.values().get("configuration.reload.intervalSeconds"));assertEquals(false,config.values().get("features.heapUsage.enabled"));assertEquals("explicit",config.source());assertEquals(64,config.hash().length());}
 @Test void canonicalHashDoesNotDependOnInputOrder(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());var one=resolver.resolve(Map.of("enabled",false,"configuration.strict",true),Map.of(),"x");var two=resolver.resolve(Map.of("configuration.strict",true,"enabled",false),Map.of(),"x");assertEquals(one.hash(),two.hash());}
 @Test void rejectsUnknownWrongTypeAndRange(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("unknown",true),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("enabled","true"),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("configuration.reload.intervalSeconds",0),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("features.methodTracing.sampleRate",Double.NaN),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("features.methodTracing.sampleRate",Double.POSITIVE_INFINITY),Map.of(),"x"));}

    @Test void reloadJsonRejectsNonStringMethodFilterEntries() throws Exception {
        java.nio.file.Path file=java.nio.file.Files.createTempFile("madlava-filter", ".json");
        try {
            java.nio.file.Files.writeString(file, "{\"filters\":{\"methods\":{\"includes\":[true]}}}");
            RuntimeConfigurationManager manager=new RuntimeConfigurationManager(new ConfigurationResolver(ConfigurationMetadata.baseline()), java.util.Map.of(), "test");
            RuntimeConfigurationManager.UpdateResult result=manager.reloadJson(file, java.util.Map.of());
            assertFalse(result.applied());
            assertTrue(result.reason().contains("Method filter entries must be strings"));
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

 @Test void rejectsBlankOutputDirectory(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("output.directory","   "),Map.of(),"x"));}

 @Test void sectionRowLimitsInheritTheEffectiveGlobalLimitUnlessExplicit(){
     var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());
     var inherited=resolver.resolve(Map.of("reporting.human.maxRows",20),Map.of(),"x");
     assertEquals(20,inherited.values().get("reporting.human.sections.methodProfiling.maxRows"));
     assertEquals(20,inherited.values().get("reporting.human.sections.diagnostics.maxRows"));
     var explicit=resolver.resolve(Map.of("reporting.human.maxRows",20,"reporting.human.sections.methodProfiling.maxRows",7),Map.of(),"x");
     assertEquals(7,explicit.values().get("reporting.human.sections.methodProfiling.maxRows"));
     assertEquals(20,explicit.values().get("reporting.human.sections.argumentGroups.maxRows"));
 }
}
