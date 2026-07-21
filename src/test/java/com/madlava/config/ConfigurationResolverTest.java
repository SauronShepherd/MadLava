package com.madlava.config;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class ConfigurationResolverTest {
 @Test void appliesDefaultsFileAndScalarOverrideInOrder(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());var config=resolver.resolve(Map.of("configuration.reload.intervalSeconds",60),Map.of("configuration.reload.intervalSeconds","90","features.heapUsage.enabled","false"),"explicit");assertEquals(90,config.values().get("configuration.reload.intervalSeconds"));assertEquals(false,config.values().get("features.heapUsage.enabled"));assertEquals("explicit",config.source());assertEquals(64,config.hash().length());}
 @Test void canonicalHashDoesNotDependOnInputOrder(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());var one=resolver.resolve(Map.of("enabled",false,"configuration.strict",true),Map.of(),"x");var two=resolver.resolve(Map.of("configuration.strict",true,"enabled",false),Map.of(),"x");assertEquals(one.hash(),two.hash());}
 @Test void rejectsUnknownWrongTypeAndRange(){var resolver=new ConfigurationResolver(ConfigurationMetadata.baseline());assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("unknown",true),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("enabled","true"),Map.of(),"x"));assertThrows(IllegalArgumentException.class,()->resolver.resolve(Map.of("configuration.reload.intervalSeconds",0),Map.of(),"x"));}
}
