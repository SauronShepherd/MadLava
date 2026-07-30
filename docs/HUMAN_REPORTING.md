# Human-readable diagnostic reports

MadLava provides a presentation API separate from machine-readable statistics:

```java
String report = com.madlava.api.MadLavaReport.reportText();
String delta = com.madlava.api.MadLavaReport.reportSinceText(checkpoint);
String markdown = com.madlava.api.MadLavaReport.reportMarkdown();
```

The report identifies its trigger (`MANUAL`) and statistics mode
(`CUMULATIVE` or `CHECKPOINT_DELTA`). It uses an engine-independent ASCII table
renderer and consumes the same in-memory snapshot state as JSONL and
`MadLavaStatistics`; it does not create Spark objects or invoke application
rendering methods.

Method profiling and Spark serialization sections are rendered when available.
COUNT_BY_ARGS uses canonical, already-aggregated argument groups. The existing
JSON statistics APIs remain the preferred interface for programmatic consumers.
