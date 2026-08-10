package com.madlava.verification;

import com.madlava.config.AgentOptions;
import com.madlava.serialization.SparkSerializationProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AgentOptionsTest {
    @TempDir
    Path directory;

    @Test
    void jsonConfigurationIsReadAndCompactArgumentsOverrideIt() throws Exception {
        Path configuration = directory.resolve("madlava.json");
        Files.writeString(configuration, "{\n"
                + "  \"output\": {\"directory\": \"from-json\"},\n"
                + "  \"features\": {\n"
                + "    \"methodProfiling\": {\"enabled\": true, \"maxEntries\": 17},\n"
                + "    \"sparkSerialization\": {\"enabled\": true, \"profile\": \"BOUNDARY\", \"rootClasses\": false, \"maxGroups\": 19}\n"
                + "  },\n"
                + "  \"filters\": {\"methods\": {\"includes\": [\"a.A.one\", \"b.B.*\"], \"excludes\": [\"b.B.noisy\"]}}\n"
                + "}");

        AgentOptions options = AgentOptions.parse(
                "config=" + configuration + ",methodMaxEntries=23");

        assertTrue(options.methodProfilingEnabled());
        assertEquals(23, options.methodMaxEntries());
        assertTrue(options.sparkSerializationEnabled());
        assertEquals(SparkSerializationProfile.BOUNDARY, options.sparkSerializationProfile());
        assertFalse(options.sparkSerializationRootClasses());
        assertEquals(19, options.sparkSerializationMaxGroups());
        assertEquals("a.A.one;b.B.*", options.methodIncludes());
        assertEquals("b.B.noisy", options.methodExcludes());
    }

    @Test
    void reportLimitsUseGlobalFallbackAndSectionOverrides() throws Exception {
        Path configuration = directory.resolve("report-limits.json");
        Files.writeString(configuration, "{\"reporting\":{\"human\":{\"maxRows\":40,\"truncate\":80,\"sections\":{\"argumentGroups\":{\"maxRows\":10}}}}}");
        AgentOptions options=AgentOptions.parse("config="+configuration);
        assertEquals(40, options.reportMaxRows()); assertEquals(10, options.reportSectionMaxRows("argumentGroups"));
        assertEquals(40, options.reportSectionMaxRows("sparkSerialization")); assertEquals(80, options.reportTruncate());
    }

    @Test
    void negativeReportLimitIsRejected() throws Exception {
        Path configuration = directory.resolve("invalid-report-limits.json");
        Files.writeString(configuration, "{\"reporting\":{\"human\":{\"maxRows\":-1}}}");
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("config="+configuration));
    }
    @Test
    void jsonIntegerSettingsRejectFractionalNumbersInsteadOfTruncating() throws Exception {
        Path positive = directory.resolve("fractional-positive.json");
        Files.writeString(positive, "{\"features\":{\"methodProfiling\":{\"maxEntries\":1.9}}}");
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("config="+positive));

        Path nonNegative = directory.resolve("fractional-nonnegative.json");
        Files.writeString(nonNegative, "{\"reporting\":{\"human\":{\"maxRows\":2.5}}}");
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("config="+nonNegative));
    }

    @Test
    void jsonMethodFilterArraysRequireStrings() throws Exception {
        Path configuration = directory.resolve("invalid-filter.json");
        Files.writeString(configuration, "{\"filters\":{\"methods\":{\"includes\":[123]}}}");
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("config="+configuration));
    }

    @Test
    void jsonConfigurationRejectsUnknownPropertiesAtStartup() throws Exception {
        Path configuration = directory.resolve("unknown-property.json");
        Files.writeString(configuration,
                "{\"features\":{\"methodProfiling\":{\"enabld\":true}}}");
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse("config=" + configuration));
    }

    @Test
    void compactArgumentsSupportQuotedCommasAndRejectDuplicates() {
        AgentOptions options=AgentOptions.parse("output=\""+directory.resolve("with,comma")+"\",methodProfiling");
        assertEquals(directory.resolve("with,comma").toAbsolutePath().normalize(),options.outputDirectory());
        assertTrue(options.methodProfilingEnabled());
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("methodProfiling=true,methodProfiling=false"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("output=\"unterminated"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("methodProfiling=treu"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("methodTracingSampleRate=2"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("sparkSerializationProfile=TYPO"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("methodProfilng=true"));
        assertThrows(IllegalArgumentException.class,()->AgentOptions.parse("reportMaxRows.typo=5"));
    }

}
