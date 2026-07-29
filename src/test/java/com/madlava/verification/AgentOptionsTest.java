package com.madlava.verification;

import com.madlava.config.AgentOptions;
import com.madlava.serialization.SparkSerializationProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
