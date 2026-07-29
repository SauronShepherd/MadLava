package com.madlava.serialization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import static com.madlava.serialization.SparkSerializationTarget.ByteMode.INPUT_BYTE_BUFFER;
import static com.madlava.serialization.SparkSerializationTarget.ByteMode.RETURNED_BYTE_BUFFER;
import static com.madlava.serialization.SparkSerializationTarget.ByteMode.UNAVAILABLE;
import static com.madlava.serialization.SparkSerializationTarget.RootMode.ENTRY_ARGUMENT;
import static com.madlava.serialization.SparkSerializationTarget.RootMode.NOT_APPLICABLE;
import static com.madlava.serialization.SparkSerializationTarget.RootMode.RETURN_VALUE;

/**
 * Exact Spark 3.5.x and Spark 4.x serializer bytecode plan.
 *
 * <p>The shared erased JVM signatures are certified against Spark 3.5.9 on Scala 2.12
 * and 2.13, plus the latest maintained Spark 4 lines on Scala 2.13. Exact descriptor
 * matching is deliberate: a changed Spark signature is reported as missing rather
 * than being instrumented fuzzily.</p>
 */
public final class SparkSerializationPlan {
    private static final String CLASS_TAG = "Lscala/reflect/ClassTag;";
    private static final String SERIALIZER_INSTANCE = "Lorg/apache/spark/serializer/SerializerInstance;";
    private static final String SERIALIZATION_STREAM = "Lorg/apache/spark/serializer/SerializationStream;";
    private static final String DESERIALIZATION_STREAM = "Lorg/apache/spark/serializer/DeserializationStream;";

    private final SparkSerializationProfile profile;
    private final List<SparkSerializationTarget> targets;
    private final Map<Integer, SparkSerializationTarget> byId;
    private final Set<Integer> matchedTargets = ConcurrentHashMap.newKeySet();
    private final Set<String> visitedTargetClasses = ConcurrentHashMap.newKeySet();
    private final LongAdder transformedClasses = new LongAdder();
    private final LongAdder transformationFailures = new LongAdder();

    public SparkSerializationPlan(SparkSerializationProfile profile) {
        this.profile = profile;
        List<SparkSerializationTarget> all = buildTargets();
        List<SparkSerializationTarget> selected = new ArrayList<>();
        Map<Integer, SparkSerializationTarget> index = new LinkedHashMap<>();
        for (SparkSerializationTarget target : all) {
            if (profile.accepts(target.layer())) {
                selected.add(target);
                index.put(target.id(), target);
            }
        }
        this.targets = Collections.unmodifiableList(selected);
        this.byId = Collections.unmodifiableMap(index);
    }

    public Optional<SparkSerializationTarget> find(String owner, String name, String descriptor) {
        return targets.stream().filter(target -> target.matches(owner, name, descriptor)).findFirst();
    }

    public SparkSerializationTarget target(int id) {
        return byId.get(id);
    }

    public boolean mayMatchClass(String internalName) {
        return targets.stream().anyMatch(target -> target.ownerInternalName().equals(internalName));
    }

    public void classVisited(String internalName) {
        if (mayMatchClass(internalName)) {
            visitedTargetClasses.add(internalName);
        }
    }

    public void targetMatched(int targetId) {
        matchedTargets.add(targetId);
    }

    public void classTransformed() {
        transformedClasses.increment();
    }

    public void transformationFailed() {
        transformationFailures.increment();
    }

    public Map<String, Object> coverageReport() {
        List<Map<String, Object>> targetReports = new ArrayList<>();
        for (SparkSerializationTarget target : targets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", target.id());
            item.put("owner", target.owner());
            item.put("method", target.methodName());
            item.put("descriptor", target.descriptor());
            item.put("layer", target.layer().name());
            item.put("operation", target.operation().name());
            item.put("matched", matchedTargets.contains(target.id()));
            item.put("classObserved", visitedTargetClasses.contains(target.ownerInternalName()));
            targetReports.add(item);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("adapter", "SPARK_3_5_AND_4_EXACT_SERIALIZER_SIGNATURES");
        report.put("supportedSparkLines", List.of("3.5.x", "4.x"));
        report.put("certifiedVersions", List.of("3.5.9", "4.0.4", "4.1.3", "4.2.0"));
        report.put("certifiedScalaBinaryVersions", List.of("2.12", "2.13"));
        report.put("profile", profile.name());
        report.put("transformedClasses", transformedClasses.sum());
        report.put("transformationFailures", transformationFailures.sum());
        report.put("targets", targetReports);
        return report;
    }

    private static List<SparkSerializationTarget> buildTargets() {
        List<SparkSerializationTarget> result = new ArrayList<>();
        int id = 1;
        for (String implementation : List.of("Java", "Kryo")) {
            String serializer = "org/apache/spark/serializer/" + implementation + "Serializer";
            String instance = "org/apache/spark/serializer/" + implementation + "SerializerInstance";
            String serializationStream = "org/apache/spark/serializer/" + implementation + "SerializationStream";
            String deserializationStream = "org/apache/spark/serializer/" + implementation + "DeserializationStream";

            result.add(new SparkSerializationTarget(
                    id++, serializer, "newInstance", "()" + SERIALIZER_INSTANCE,
                    SparkSerializationOperation.NEW_INSTANCE, SparkSerializationLayer.BOUNDARY,
                    -1, NOT_APPLICABLE, UNAVAILABLE));
            result.add(new SparkSerializationTarget(
                    id++, instance, "serialize", "(Ljava/lang/Object;" + CLASS_TAG + ")Ljava/nio/ByteBuffer;",
                    SparkSerializationOperation.SERIALIZE, SparkSerializationLayer.BOUNDARY,
                    0, ENTRY_ARGUMENT, RETURNED_BYTE_BUFFER));
            result.add(new SparkSerializationTarget(
                    id++, instance, "deserialize", "(Ljava/nio/ByteBuffer;" + CLASS_TAG + ")Ljava/lang/Object;",
                    SparkSerializationOperation.DESERIALIZE, SparkSerializationLayer.BOUNDARY,
                    0, RETURN_VALUE, INPUT_BYTE_BUFFER));
            result.add(new SparkSerializationTarget(
                    id++, instance, "deserialize", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;" + CLASS_TAG + ")Ljava/lang/Object;",
                    SparkSerializationOperation.DESERIALIZE, SparkSerializationLayer.BOUNDARY,
                    0, RETURN_VALUE, INPUT_BYTE_BUFFER));
            result.add(new SparkSerializationTarget(
                    id++, instance, "serializeStream", "(Ljava/io/OutputStream;)" + SERIALIZATION_STREAM,
                    SparkSerializationOperation.SERIALIZE_STREAM_FACTORY, SparkSerializationLayer.BOUNDARY,
                    -1, NOT_APPLICABLE, UNAVAILABLE));
            result.add(new SparkSerializationTarget(
                    id++, instance, "deserializeStream", "(Ljava/io/InputStream;)" + DESERIALIZATION_STREAM,
                    SparkSerializationOperation.DESERIALIZE_STREAM_FACTORY, SparkSerializationLayer.BOUNDARY,
                    -1, NOT_APPLICABLE, UNAVAILABLE));
            if ("Java".equals(implementation)) {
                result.add(new SparkSerializationTarget(
                        id++, instance, "deserializeStream", "(Ljava/io/InputStream;Ljava/lang/ClassLoader;)" + DESERIALIZATION_STREAM,
                        SparkSerializationOperation.DESERIALIZE_STREAM_FACTORY, SparkSerializationLayer.BOUNDARY,
                        -1, NOT_APPLICABLE, UNAVAILABLE));
            }
            result.add(new SparkSerializationTarget(
                    id++, serializationStream, "writeObject", "(Ljava/lang/Object;" + CLASS_TAG + ")" + SERIALIZATION_STREAM,
                    SparkSerializationOperation.WRITE_OBJECT, SparkSerializationLayer.STREAM,
                    0, ENTRY_ARGUMENT, UNAVAILABLE));
            result.add(new SparkSerializationTarget(
                    id++, deserializationStream, "readObject", "(" + CLASS_TAG + ")Ljava/lang/Object;",
                    SparkSerializationOperation.READ_OBJECT, SparkSerializationLayer.STREAM,
                    -1, RETURN_VALUE, UNAVAILABLE));
        }
        return result;
    }
}
