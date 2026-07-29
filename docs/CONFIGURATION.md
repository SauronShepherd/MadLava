# MadLava configuration

MadLava `0.1.0` accepts either compact `-javaagent` options or a JSON configuration file. The complete starting point is [madlava.json.example](../madlava.json.example).

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
