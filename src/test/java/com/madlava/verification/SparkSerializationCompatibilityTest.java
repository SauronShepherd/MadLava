package com.madlava.verification;

import com.madlava.serialization.SparkSerializationPlan;
import com.madlava.serialization.SparkSerializationProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SparkSerializationCompatibilityTest {
    @Test
    void planDeclaresTheCertifiedSpark35AndSpark4Matrix() {
        Map<String, Object> report = new SparkSerializationPlan(SparkSerializationProfile.ALL).coverageReport();
        assertEquals("SPARK_3_5_AND_4_EXACT_SERIALIZER_SIGNATURES", report.get("adapter"));
        assertEquals(List.of("3.5.x", "4.x"), report.get("supportedSparkLines"));
        assertEquals(List.of("3.5.9", "4.0.4", "4.1.3", "4.2.0"), report.get("certifiedVersions"));
        assertEquals(List.of("2.12", "2.13"), report.get("certifiedScalaBinaryVersions"));
        assertTrue(((List<?>) report.get("targets")).size() >= 16);
    }
}
