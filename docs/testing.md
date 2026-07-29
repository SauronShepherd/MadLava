# Testing and certification

Iteration-12 uses no notebooks. All acceptance evidence is produced by deterministic unit tests and fresh forked JVMs.

## Test layers

### Transformer unit tests

`MethodTracingTransformerTest` transforms a compiled fixture and verifies every JVM return category, internal versus escaping exceptions, exact `Throwable` identity, recursion, synchronization, overload descriptors, filtering, timing, and state preservation.

### Serialization bridge unit tests

`SparkSerializationBridgeTest` verifies exact returned/input `ByteBuffer` attribution, byte-accuracy labels, bounded root classes, nested suppression, non-negative timing, and fail-open behaviour.

### Real Spark integration tests

`SparkSerializerAgentIT` starts a new child JVM with the packaged shaded agent for each case. It loads the official `spark-core` artifact selected by the active profile and executes real Java and Kryo serializer boundaries and streams.

Each lane verifies:

1. The runtime Spark version equals the profile's pinned artifact.
2. The runtime Scala binary version matches the artifact suffix.
3. Java and Kryo boundary round trips preserve payload equality.
4. Stream round trips preserve payload equality.
5. Generic method tracing and Spark-specific analysis operate together.
6. Serialize and deserialize expose exact positive `ByteBuffer` counts.
7. Stream rows remain `UNAVAILABLE` for bytes.
8. Coverage reports transformed classes and zero transformation failures.
9. The report identifies the runtime as a supported Spark/Scala combination.
10. A real registration-required Kryo failure is classified exceptionally.
11. Payload values never appear in schema-v1 JSONL.

## Certification matrix

| Profile | Spark artifact | Scala | Required Java |
|---|---|---|---|
| `spark35-it` | `spark-core_2.12:3.5.9` | 2.12 | 11 or 17 |
| `spark35-scala213-it` | `spark-core_2.13:3.5.9` | 2.13 | 11 or 17 |
| `spark40-it` | `spark-core_2.13:4.0.4` | 2.13 | 17 or 21 |
| `spark41-it` | `spark-core_2.13:4.1.3` | 2.13 | 17 or 21 |
| `spark42-it` | `spark-core_2.13:4.2.0` | 2.13 | 17 or 21 |

GitHub Actions runs the same matrix. Spark profiles are executed separately to prevent Scala 2.12/2.13 classpath contamination.

## Commands

```bash
mvn -B clean verify
mvn -B -Pspark35-it clean verify
mvn -B -Pspark35-scala213-it clean verify
mvn -B -Pspark40-it clean verify
mvn -B -Pspark41-it clean verify
mvn -B -Pspark42-it clean verify
```

Or execute every lane:

```bash
scripts/verify-spark-matrix.sh
```

## Release discipline

When Apache Spark publishes a newer maintenance release:

1. update the relevant profile pin;
2. run `scripts/inspect-spark-signatures.sh` against its JARs;
3. run that profile on every certified JDK;
4. update the coverage metadata and documentation only after the lane passes.

A line is never declared supported merely because source-level methods look similar.
