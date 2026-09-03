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
    @Test void unchangedReloadDoesNotAdvanceVersionOrCallListeners(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        long version=manager.current().version();
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        manager.addListener((before,after)->calls.incrementAndGet());
        RuntimeConfigurationManager.UpdateResult result=manager.reload(manager.current().values(),Map.of(),"touch");
        assertTrue(result.applied()); assertEquals("UNCHANGED",result.reason());
        assertEquals(version,manager.current().version()); assertEquals(0,calls.get());
    }

    @Test void explicitOverridesSurviveFileReload(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test",
                Map.of("output.directory","explicit-output"));
        RuntimeConfigurationManager.UpdateResult result=manager.reload(
                Map.of("output.directory","file-output"),Map.of(),"file");
        assertTrue(result.applied()); assertEquals("explicit-output",manager.current().values().get("output.directory"));
    }

    @Test void rejectedTransitionDoesNotConsumeVersionOrReplaceState(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        manager.addTransitionValidator((before,proposed)->
                java.util.Objects.equals(before.values().get("enabled"),proposed.values().get("enabled"))?null:"RESTART_REQUIRED: enabled");
        long version=manager.current().version();
        RuntimeConfigurationManager.UpdateResult result=manager.reload(Map.of("enabled",false),Map.of(),"reload");
        assertFalse(result.applied()); assertEquals("RESTART_REQUIRED: enabled",result.reason());
        assertEquals(version,manager.current().version()); assertEquals(true,manager.current().values().get("enabled"));
    }

    @Test void jsonReloadPreservesUsefulValidationReason() throws Exception {
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        java.nio.file.Path file=Files.createTempFile("madlava-invalid", ".json");
        try {
            Files.writeString(file,"{\"unknown\":true}");
            RuntimeConfigurationManager.UpdateResult result=manager.reloadJson(file,Map.of());
            assertFalse(result.applied());
            assertTrue(result.reason().contains("Unknown configuration property"));
        } finally { Files.deleteIfExists(file); }
    }

    @Test void concurrentReloadsNeverMoveVersionBackwards() throws Exception {
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        java.util.List<Long> observed=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        manager.addListener((before,after)->observed.add(after.version()));
        java.util.List<Thread> threads=new java.util.ArrayList<>();
        for(int i=0;i<32;i++){final int n=i;Thread thread=new Thread(()->manager.reload(
                Map.of("reporting.intervalMillis",1000+n),Map.of(),"reload-"+n));threads.add(thread);thread.start();}
        for(Thread thread:threads)thread.join();
        java.util.List<Long> sorted=new java.util.ArrayList<>(observed);java.util.Collections.sort(sorted);
        assertEquals(sorted,observed); assertEquals(1L+observed.size(),manager.current().version());
    }

    @Test void differentMapsCannotCollideThroughDelimiterLikeStringValues(){
        Map<String,Object> first=new java.util.LinkedHashMap<>();
        first.put("filters.methods.excludes","x, filters.methods.includes=y");
        first.put("filters.methods.includes","z");
        Map<String,Object> second=new java.util.LinkedHashMap<>();
        second.put("filters.methods.excludes","x");
        second.put("filters.methods.includes","y, filters.methods.includes=z");
        // TreeMap.toString() was identical for these values; it must never be configuration identity.
        assertEquals(new java.util.TreeMap<>(first).toString(),new java.util.TreeMap<>(second).toString());
        assertNotEquals(new EffectiveConfiguration(first,"a").hash(),new EffectiveConfiguration(second,"b").hash());
    }

    @Test void listenerSideEffectFailureIsObservableInsteadOfSilentlyClean(){
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        manager.addListener((before,after)->{throw new IllegalStateException("simulated side-effect failure");});
        RuntimeConfigurationManager.UpdateResult result=manager.reload(Map.of("enabled",false),Map.of(),"reload");
        assertTrue(result.applied());
        assertEquals("APPLIED_WITH_LISTENER_FAILURE",result.reason());
        assertEquals(1L,manager.listenerFailures());
        assertEquals(false,manager.current().values().get("enabled"));
    }

    @Test void jsonFileRejectsLiteralDottedPropertyNamesThatStartupCannotInterpret() throws Exception {
        RuntimeConfigurationManager manager=new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()),Map.of(),"test");
        java.nio.file.Path file=Files.createTempFile("madlava-dotted", ".json");
        try {
            Files.writeString(file,"{\"output.directory\":\"somewhere-else\"}");
            RuntimeConfigurationManager.UpdateResult result=manager.reloadJson(file,Map.of());
            assertFalse(result.applied());
            assertTrue(result.reason().contains("must not contain '.'"));
        } finally { Files.deleteIfExists(file); }
    }

}
