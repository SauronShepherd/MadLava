package com.madlava.verification;

import com.madlava.serialization.ByteAccuracy;
import com.madlava.serialization.SparkSerializationBridge;
import com.madlava.serialization.SparkSerializationMetrics;
import com.madlava.serialization.SparkSerializationPlan;
import com.madlava.serialization.SparkSerializationProfile;
import com.madlava.serialization.SparkSerializationTarget;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SparkSerializationBridgeTest {
    @Test
    void recordsExactByteBufferBytesAndSuppressesNestedLayerDoubleCounting() {
        SparkSerializationPlan plan = new SparkSerializationPlan(SparkSerializationProfile.ALL);
        SparkSerializationMetrics metrics = new SparkSerializationMetrics(32);
        SparkSerializationBridge.configure(plan, metrics, true);

        SparkSerializationTarget serialize = plan.find(
                "org/apache/spark/serializer/JavaSerializerInstance",
                "serialize",
                "(Ljava/lang/Object;Lscala/reflect/ClassTag;)Ljava/nio/ByteBuffer;")
                .orElseThrow();
        SparkSerializationTarget nestedWrite = plan.find(
                "org/apache/spark/serializer/JavaSerializationStream",
                "writeObject",
                "(Ljava/lang/Object;Lscala/reflect/ClassTag;)Lorg/apache/spark/serializer/SerializationStream;")
                .orElseThrow();

        Object payload = List.of("lava", "spark");
        long outer = SparkSerializationBridge.enter(serialize.id(), payload);
        long nested = SparkSerializationBridge.enter(nestedWrite.id(), payload);
        SparkSerializationBridge.success(null, nested);
        SparkSerializationBridge.success(ByteBuffer.wrap(new byte[37]), outer);

        Map<String, Object> report = metrics.report(plan);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) report.get("groups");
        assertEquals(1, groups.size());
        Map<String, Object> group = groups.get(0);
        assertEquals("SERIALIZE", group.get("operation"));
        assertEquals(ByteAccuracy.EXACT_RETURNED_BYTEBUFFER.name(), group.get("byteAccuracy"));
        assertEquals(37L, ((Number) group.get("observedBytes")).longValue());
        assertEquals(1L, ((Number) group.get("nestedOperationsSuppressed")).longValue());
        assertTrue(((Number) group.get("totalDurationNanos")).longValue() >= 0L);
    }
    @Test
    void dropsNewGroupsAfterTheConfiguredCardinalityLimit() {
        SparkSerializationPlan plan = new SparkSerializationPlan(SparkSerializationProfile.BOUNDARY);
        SparkSerializationMetrics metrics = new SparkSerializationMetrics(1);
        SparkSerializationBridge.configure(plan, metrics, true);

        SparkSerializationTarget serialize = plan.find(
                "org/apache/spark/serializer/JavaSerializerInstance",
                "serialize",
                "(Ljava/lang/Object;Lscala/reflect/ClassTag;)Ljava/nio/ByteBuffer;")
                .orElseThrow();

        long first = SparkSerializationBridge.enter(serialize.id(), "first-root");
        SparkSerializationBridge.success(ByteBuffer.wrap(new byte[3]), first);
        long second = SparkSerializationBridge.enter(serialize.id(), List.of("second-root"));
        SparkSerializationBridge.success(ByteBuffer.wrap(new byte[5]), second);

        Map<String, Object> report = metrics.report(plan);
        assertEquals(1, ((Number) report.get("activeGroups")).intValue());
        assertEquals(1L, ((Number) report.get("droppedGroups")).longValue());
    }

}
