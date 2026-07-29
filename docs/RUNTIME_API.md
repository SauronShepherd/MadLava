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
runtime configuration change.

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
