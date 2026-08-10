# Runtime Statistics API

MadLava exposes a Py4J-friendly in-process bridge at
`com.madlava.api.MadLavaStatistics`. It is read-only and uses the same
`AgentRuntime.snapshot()` source as JSONL reporting; it never creates Spark
objects or writes files.

```python
import json

api = spark.sparkContext._jvm.com.madlava.api.MadLavaStatistics
print(json.loads(api.statusJson()))
checkpoint = api.checkpoint()
# run workload
stats = json.loads(api.snapshotSinceJson(checkpoint))
api.releaseCheckpoint(checkpoint)
```

Available methods include `isAvailable`, `statusJson`, `snapshotJson`,
`methodProfilingJson`, `sparkSerializationJson`, `checkpoint`,
`snapshotSinceJson`, `methodProfilingSinceJson`,
`sparkSerializationSinceJson`, and `releaseCheckpoint`.

Checkpoints are opaque and bounded to 64 active entries; the oldest entry is
evicted when the limit is reached. Unknown or released checkpoint IDs return a
JSON error envelope. The API remains JVM-scoped and is independent of Spark
Context stop/restart and JSONL persistence.

The bridge, bounded checkpoint registry, and per-method/per-serialization-group
delta calculation are implemented. `COUNT_BY_ARGS` groups are returned already
aggregated, so consumers do not need to reconstruct counts from events. Delta
responses include configuration-version metadata when a checkpoint spans a
runtime configuration change. Nested `COUNT_BY_ARGS` rows are delta-calculated
with their parent method, and unchanged rows are omitted from interval results.

For duration statistics, additive totals are subtracted and the interval average
is recomputed from the interval total and completion count. Minimum and maximum
durations cannot in general be reconstructed from two cumulative snapshots; for
a row that already existed at the checkpoint they are therefore returned as
`null` in delta results instead of an incorrect subtraction. Rows first created
after the checkpoint retain their exact minimum and maximum values.

## Runtime configuration bridge

`com.madlava.api.MadLavaConfiguration` exposes the shared configuration manager:

```python
config = spark.sparkContext._jvm.com.madlava.api.MadLavaConfiguration
version = config.configurationVersion()
effective = json.loads(config.effectiveConfigurationJson())
result = json.loads(config.setOutputPath("./diagnostic-output"))
```

`reload()` reloads the original file-backed source when available. Configuration
changes are applied atomically and use the same reporter rotation path as the
file watcher. Statistics queries remain read-only and do not require JSONL
reporting to be enabled.
