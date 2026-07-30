package com.madlava.api;

import com.madlava.config.AgentOptions;
import com.madlava.reporting.AgentRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MadLavaScopesTest {
    private AgentRuntime runtime;

    @AfterEach void cleanup() { if (runtime != null) MadLavaRuntimeRegistry.clear(runtime); }

    @Test void invalidNamesAndUnknownScopesAreDeterministic() {
        runtime = new AgentRuntime("test", "hash", AgentOptions.parse(""), null, null, null);
        assertTrue(MadLavaRuntimeRegistry.register(runtime));
        assertTrue(MadLavaScopes.beginScope(" ").contains("INVALID_SCOPE_NAME"));
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
}
