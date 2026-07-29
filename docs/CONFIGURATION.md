# MadLava configuration

MadLava `0.1.0` accepts either compact `-javaagent` options or a JSON configuration file. The complete starting point is [madlava.json.example](../madlava.json.example).

## Runtime configuration and hot reload

`RuntimeConfigurationManager` keeps an immutable effective configuration in an
`AtomicReference`. Accepted updates receive a monotonically increasing version
and SHA-256 hash; rejected updates leave the previous configuration active.
Set `configuration.hotReload.enabled` to `true` in a file-backed configuration
to start the agent's daemon `ConfigurationWatcher`. It uses `WatchService` with
debounce support for normal and replace-style editor saves. Invalid or
temporarily missing files are ignored safely, and the watcher is closed by the
agent shutdown hook.

The configuration core, method rule model, safe renderer, sampling, and
redaction primitives are available. Reporter output switching, streamed trace
records, and JVM retransformation orchestration remain the next integration
step; the existing reporter remains startup-configured for now.

## Method observation rules

Existing `Class.method` rules remain aggregate `COUNT` rules and do not render
arguments. The explicit `Class.method(*)` suffix selects aggregate
`COUNT_BY_ARGS` semantics in the structured method-rule model. It is not a
wildcard for arbitrary method names and does not emit one event per invocation.
JVM descriptors may be supplied with `#`, for example:

```text
com.example.Parser.parse#(Ljava/lang/String;)V
com.example.Parser.parse(*)
```

The default `SAFE` renderer does not call `toString()` on arbitrary application
objects; it uses bounded primitive/collection rendering and type/identity
fallbacks. `TO_STRING` is explicit opt-in and failure-protected. Sampling rates
range from `0` to `1`, while redaction supports zero-based argument indexes and
compiled regular-expression patterns.

Set `features.methodTracing.enabled` to `true` to emit non-argument TRACE
events for selected methods. It is disabled by default; enabling ordinary
method profiling does not enable tracing.

Use `Class.method(*)` for aggregate `COUNT_BY_ARGS`. It preserves exact method
totals and groups equal rendered argument tuples with bounded cardinality. This
is distinct from per-invocation `TRACE_ARGS` events.

## JSON configuration

Copy the example and adjust the output directory and method filters:

```text
madlava.json.example -> madlava.json
```

Attach it to a JVM with:

```text
java -javaagent:/absolute/path/madlava-agent-0.1.0.jar=config=/absolute/path/madlava.json -jar application.jar
```

Explicit agent options override values loaded from the JSON file. The current JSON sections are:

| Section | Purpose |
|---|---|
| `output.directory` | Root directory for MadLava evidence |
| `reporting.snapshotIntervalSeconds` | Periodic snapshot interval |
| `reporting.shutdownSnapshotOnly` | Emit only the shutdown snapshot when `true` |
| `features.methodProfiling` | Enable bounded JVM method aggregation |
| `features.sparkSerialization` | Enable Java/Kryo serializer boundary profiling |
| `filters.methods.includes` | Semicolon-separated method patterns after loading |
| `filters.methods.excludes` | Patterns that override includes |

## Spark and PySpark

The agent must be attached to the Spark driver before the `SparkSession` is created. In PySpark, configure the driver JVM through `spark.driver.extraJavaOptions`:

```python
from pyspark.sql import SparkSession

spark = (
    SparkSession.builder
    .config(
        "spark.driver.extraJavaOptions",
        "-javaagent:/absolute/path/madlava-agent-0.1.0.jar="
        "config=/absolute/path/madlava.json"
    )
    .getOrCreate()
)
```

If a Spark session already exists, restart the session or kernel. Changing the configuration afterward cannot attach an agent to an already-running driver JVM.

### Verified compatibility

- Spark 3.5.9 / Scala 2.12 and 2.13: JVM serializer integration is verified.
- Spark 4.0.4 and 4.1.3 / Scala 2.13: JVM serializer integration is verified.
- PySpark 4.0.1 with Python 3.11, PyArrow 15.0.2, and Pandas 2.0.3: local Arrow DataFrame smoke test is verified.
- PySpark 3.5.x Arrow `toPandas()` is not certified on the current Windows setup because the JVM Arrow allocator reports a leak.

Configure `PYSPARK_PYTHON` and `PYSPARK_DRIVER_PYTHON` explicitly when `python` is not on `PATH`.

## Output files

MadLava does not generate `output.trc`. It writes schema-v1 JSONL evidence using a unique directory per JVM run:

```text
<output.directory>/madlava-run-<pid>.json
<output.directory>/run-<pid>-<nonce>/madlava.jsonl
```

`madlava-run.json` is a small discovery manifest containing the PID and absolute report path. The nested run directory prevents concurrent Spark drivers from mixing evidence into one file.

The agent prints the exact report path to stderr during startup:

```text
MadLava 0.1.0 Iteration-12 ready; report=...
```

If neither manifest nor report appears, check that the driver can write to `output.directory`, that the agent JAR exists, and that the startup stderr does not contain `MadLava Iteration-12 bootstrap disabled`.
