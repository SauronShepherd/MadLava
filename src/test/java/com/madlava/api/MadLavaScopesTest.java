package com.madlava.api;

import com.madlava.config.AgentOptions;
import com.madlava.config.ConfigurationMetadata;
import com.madlava.config.ConfigurationResolver;
import com.madlava.config.RuntimeConfigurationManager;
import java.util.Map;
import com.madlava.reporting.AgentRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MadLavaScopesTest {
    private AgentRuntime runtime;

    @AfterEach void cleanup() { MadLavaScopes.resetForTests(); MadLavaStatistics.resetForTests(); if (runtime != null) MadLavaRuntimeRegistry.clear(runtime); }

    @Test void invalidNamesAndUnknownScopesAreDeterministic() {
        runtime = new AgentRuntime("test", "hash", AgentOptions.parse(""), null, null, null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        assertTrue(MadLavaScopes.beginScope(" ").contains("INVALID_SCOPE_NAME"));
        assertTrue(MadLavaScopes.beginScope("forged\nConfiguration Version: 999").contains("INVALID_SCOPE_NAME"));
        assertTrue(MadLavaScopes.endScope("does-not-exist").contains("UNKNOWN_SCOPE_OR_ALREADY_ENDED"));
    }

    @Test void scopeCanCompleteWithoutSparkAndResultCanBeReleased() {
        runtime = new AgentRuntime("test", "hash", AgentOptions.parse(""), null, null, null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        String scope = MadLavaScopes.beginScope("unit");
        assertTrue(scope.startsWith("scope-"));
        String result = MadLavaScopes.endScope(scope);
        assertTrue(result.startsWith("scope-result-"));
        assertTrue(MadLavaScopes.scopeResultJson(result).contains("\"scopeName\":\"unit\""));
        String report = MadLavaReport.scopeReportText(result);
        assertTrue(report.contains("SCOPE_END"));
        assertTrue(report.contains("SCOPE_DELTA"));
        assertTrue(report.contains("Scope ID: " + scope));
        assertTrue(report.contains("Result ID: " + result));
        assertTrue(MadLavaScopes.releaseScopeResult(result));
        assertTrue(MadLavaScopes.scopeResultJson(result).contains("UNKNOWN_OR_EXPIRED_SCOPE_RESULT"));
    }

    @Test void scopeMetadataTracksTheCheckpointAndEndConfigurationVersions() {
        RuntimeConfigurationManager configuration = new RuntimeConfigurationManager(
                new ConfigurationResolver(ConfigurationMetadata.baseline()), Map.of(), "test");
        runtime = new AgentRuntime("test", "hash", AgentOptions.parse(""), null, null, null, configuration, false);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        long startVersion = configuration.current().version();
        String scope = MadLavaScopes.beginScope("config-change");
        assertTrue(scope.startsWith("scope-"));
        RuntimeConfigurationManager.UpdateResult update = configuration.reload(
                Map.of("reporting.human.maxRows", 7), Map.of(), "test");
        assertTrue(update.applied());
        String result = MadLavaScopes.endScope(scope);
        String json = MadLavaScopes.scopeResultJson(result);
        assertTrue(json.contains("\"configurationVersionAtStart\":" + startVersion));
        assertTrue(json.contains("\"configurationVersionAtEnd\":" + update.state().version()));
        assertTrue(json.contains("\"configurationChanged\":true"));
    }

    @Test void concurrentBeginNeverExceedsActiveScopeLimit() throws Exception {
        runtime=new AgentRuntime("test","hash",AgentOptions.parse(""),null,null,null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        java.util.List<String> results=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.List<Thread> threads=new java.util.ArrayList<>();
        for(int i=0;i<100;i++){final int n=i;Thread t=new Thread(()->results.add(MadLavaScopes.beginScope("scope-"+n)));threads.add(t);t.start();}
        for(Thread t:threads)t.join();
        long active=results.stream().filter(v->v.startsWith("scope-")).count();
        assertEquals(64L,active);
        assertEquals(36L,results.stream().filter(v->v.contains("TOO_MANY_ACTIVE_SCOPES")).count());
    }

    @Test void manualCheckpointChurnCannotEvictAnActiveScopeBaseline() {
        runtime=new AgentRuntime("test","hash",AgentOptions.parse(""),null,null,null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        String scope=MadLavaScopes.beginScope("protected");assertTrue(scope.startsWith("scope-"));
        for(int i=0;i<100;i++)MadLavaStatistics.checkpoint();
        String result=MadLavaScopes.endScope(scope);
        assertTrue(result.startsWith("scope-result-"),result);
        assertFalse(MadLavaReport.scopeReportText(result).contains("Checkpoint: protected"));
    }


    @Test void nullPublicIdsReturnErrorsInsteadOfConcurrentMapExceptions() {
        runtime = new AgentRuntime("test", "hash", AgentOptions.parse(""), null, null, null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        assertTrue(MadLavaScopes.endScope(null).contains("UNKNOWN_SCOPE_OR_ALREADY_ENDED"));
        assertTrue(MadLavaScopes.scopeResultJson(null).contains("UNKNOWN_OR_EXPIRED_SCOPE_RESULT"));
        assertTrue(MadLavaScopes.scopeStatusJson(null).contains("UNKNOWN_SCOPE"));
        assertFalse(MadLavaScopes.releaseScopeResult(null));
    }
}
