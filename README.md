# MadLava Iteration-12

Iteration-12 completes two production features inside MadLava:

1. **End-to-end JVM method tracing** with exact bytecode method identity, normal and exceptional completion, inclusive elapsed time, bounded aggregation, and fail-open probes.
2. **Spark serialization analysis** that automatically instruments real Java and Kryo serializer boundaries and reports calls, completion state, duration, bounded root-class names, exact `ByteBuffer` bytes where they are genuinely available, observation layer, nesting suppression, runtime Spark/Scala identity, and adapter coverage.

The repository contains **no notebooks and no interactive test workflow**. The article notebooks remain article assets outside MadLava. Iteration-12 is accepted only through automated unit tests and forked-JVM integration tests against official Spark artifacts.

## Spark support policy

The adapter supports the shared serializer bytecode contract used by:

- Spark `3.5.x`, Scala `2.12` or `2.13`;
- Spark `4.x`, Scala `2.13`.

The pinned Iteration-12 certification matrix uses the newest releases in the maintained lines at the time of this iteration:

| Spark | Scala | Certification JDKs | Maven profile |
|---|---|---|---|
| 3.5.9 | 2.12 | 11, 17 | `spark35-it` |
| 3.5.9 | 2.13 | 11, 17 | `spark35-scala213-it` |
| 4.0.4 | 2.13 | 17, 21 | `spark40-it` |
| 4.1.3 | 2.13 | 17, 21 | `spark41-it` |
| 4.2.0 | 2.13 | 17, 21 | `spark42-it` |

The agent itself continues to target Java 11 bytecode. Spark 4 certification runs on the Java versions supported by Spark 4.

## Build

Generic method-tracing tests:

```bash
mvn -B clean verify
```

A single real-Spark lane:

```bash
mvn -B -Pspark42-it clean verify
```

Every pinned Spark lane:

```bash
scripts/verify-spark-matrix.sh
```

Complete Iteration-12 gate:

```bash
scripts/verify-iteration12.sh
```

Do not activate two Spark integration profiles in the same Maven invocation: Spark 3.5 and Spark 4 use different Scala artifacts. The matrix script runs them in isolated builds.

## Attach the agent

```bash
java \
  -javaagent:target/madlava-agent-0.1.0.jar=\
output=target/madlava-output,\
methodProfiling=true,\
methodInclude=com.example.service.*.*,\
sparkSerialization=true,\
sparkSerializationProfile=ALL \
  -jar application.jar
```

For Spark or PySpark, copy `madlava.json.example` and attach it to the driver
before creating the `SparkSession`:

```python
spark = (SparkSession.builder
    .config("spark.driver.extraJavaOptions",
            "-javaagent:/absolute/path/madlava-agent-0.1.0.jar=config=/absolute/path/madlava.json")
    .getOrCreate())
```

Reports are written to `madlava-output/madlava-run.json` and to the unique
`madlava-output/run-<pid>-<nonce>/madlava.jsonl` directory.

Patterns are separated with semicolons. A descriptor may be appended after `#`:

```text
com.example.Service.execute#(Ljava/lang/String;)V
```

Excludes override includes. Empty includes instrument nothing.

## Spark serialization report

`sparkSerialization=true` registers an exact, dependency-free adapter before Spark serializer classes load. The report includes runtime-detected `sparkVersion`, `scalaVersion`, Java version, and whether that combination matches the supported lines.

Profiles:

- `BOUNDARY`: `newInstance`, `serialize`, `deserialize`, `serializeStream`, and `deserializeStream`.
- `STREAM`: `writeObject` and `readObject`.
- `ALL`: both layers, separately labelled.

Boundary and stream rows can describe overlapping work. MadLava keeps them separate and suppresses nested double counting inside one selected operation.

Exact bytes are reported only for returned `ByteBuffer.remaining()` after `serialize` and input `ByteBuffer.remaining()` before `deserialize`. Stream byte counts remain `UNAVAILABLE` without a trustworthy counting boundary.

## Automated proof

Every Spark profile packages the shaded agent, starts fresh child JVMs with `-javaagent`, loads the selected official Spark artifact, executes Java and Kryo boundary and stream round trips, forces a real Kryo registration failure, checks runtime Spark/Scala identity, parses schema-v1 JSONL, and fails on missing observations, payload leakage, bridge failures, or transformation failures.

## Documentation

- [Configuration and Spark/PySpark setup](docs/CONFIGURATION.md)
- [Complete 0.1.0 user guide](docs/USER_GUIDE.md)
- [Method tracing](docs/METHOD_TRACING.md)
- [Spark serialization](docs/SPARK_SERIALIZATION.md)
- [Testing and certification](docs/TESTING.md)
- [Iteration-12 release notes](docs/ITERATION_12_RELEASE_NOTES.md)
- [Iteration-11 integration notes](docs/ITERATION_11_MERGE.md)
- [Build hygiene](docs/BUILD_HYGIENE.md)
