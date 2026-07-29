# MadLava 0.1.0 User Guide

MadLava is a Java agent for focused JVM investigations. It instruments selected application methods and Spark Java/Kryo serializer boundaries, aggregates evidence in bounded registries, and writes schema-v1 JSONL snapshots without retaining application payloads.

## Implemented features

### Method profiling

- Owner/name wildcards and exact JVM descriptors using `#` syntax.
- Include/exclude filters; excludes win and empty includes mean no instrumentation.
- Normal and exceptional completion counts with monotonic inclusive duration.
- Bounded method registry and re-entrant callback suppression.
- Fail-open transformation and callback behavior.

Example:

```text
methodProfiling=true,methodInclude=com.example.orders.*;com.example.Repository.save#(Lcom/example/Order;)V
```

### Spark serialization

The generic agent remains free of mandatory Spark dependencies while recognizing Spark serializer signatures at runtime.

| Profile | Coverage |
|---|---|
| `BOUNDARY` | Serializer construction, serialize, deserialize, and boundary operations |
| `STREAM` | Java/Kryo stream read/write operations |
| `ALL` | Both profiles |

Reports distinguish Java/Kryo implementations, normal/failed operations, nested suppression, Spark/Scala identity, and trustworthy `ByteBuffer` byte counts. Payloads are never captured.

### Privacy and safety

- Arguments, return values, serializer payloads, exception messages, and private literals are not retained.
- Method and serializer cardinality is bounded.
- Callback failures are swallowed so the profiled application remains authoritative.
- Each JVM gets a unique run directory, preventing concurrent report mixing.

## Build and attach

```bash
mvn -B -Pgeneric clean package
java -javaagent:/absolute/path/target/madlava-agent-0.1.0.jar=config=/absolute/path/madlava.json -jar application.jar
```

Start with [`madlava.json.example`](../madlava.json.example). If `config=` is omitted, MadLava looks for `madlava.json` on the classpath and then in the current working directory. Explicit compact options override file values.

## Configuration options

| Option | Default | Meaning |
|---|---:|---|
| `output` | `madlava-output` | Output root |
| `snapshotIntervalSeconds` | `1` | Periodic snapshot interval |
| `shutdownSnapshotOnly` | `false` | Emit only final snapshot |
| `methodProfiling` | `false` | Enable method instrumentation |
| `methodInclude` | empty | Semicolon-separated method patterns |
| `methodExclude` | empty | Exclusion patterns |
| `methodMaxEntries` | `2048` | Maximum method groups |
| `sparkSerialization` | `false` | Enable Spark serializer analysis |
| `sparkSerializationProfile` | `ALL` | `BOUNDARY`, `STREAM`, or `ALL` |
| `sparkSerializationRootClasses` | `true` | Include bounded root type names |
| `sparkSerializationMaxGroups` | `2048` | Maximum serializer groups |
| `diagnosticsToStderr` | `true` | Print startup/report path |

## Spark and PySpark

Attach to the driver before creating the `SparkSession`:

```python
from pyspark.sql import SparkSession

spark = (SparkSession.builder
    .config("spark.driver.extraJavaOptions",
            "-javaagent:/absolute/path/madlava-agent-0.1.0.jar="
            "config=/absolute/path/madlava.json")
    .getOrCreate())
```

Restart the session/kernel after changing this setting. An already-running driver cannot be retroactively instrumented. Py4J APIs remain Spark-version-specific; use `getActiveStageIds()` rather than the nonexistent `getStageInfoIds()`.

## Output

MadLava does not generate `output.trc`. It generates:

```text
madlava-output/
├── madlava-run-<pid>.json
└── run-<pid>-<nonce>/madlava.jsonl
```

The manifest and startup stderr contain the exact report path. If no files appear, verify the JAR path, output-directory permissions, startup stderr, and that PySpark received the option through `spark.driver.extraJavaOptions`.

## Verification

```bash
mvn -B -Pgeneric clean verify
mvn -B -Pspark35-it clean verify
```

Validate a generated report offline:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/validate-report.ps1 `
  -Report .\madlava-output\run-<pid>-<nonce>\madlava.jsonl
```

The tested `0.1.0` scope is focused profiling. It does not claim the full unreleased v0.2.0 platform: cluster-wide merge, complete PySpark lifecycle correlation, bootstrap bridge instrumentation, or every historical collector are not yet active.
