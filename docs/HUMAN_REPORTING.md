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
# Ordering and display limits

Human report rows are semantically sorted before formatting and limiting:

| Section | Default order |
|---|---|
| Method Profiling | invocations descending, then owner/method/descriptor ascending |
| COUNT_BY_ARGS | invocations descending, then canonical arguments ascending |
| Spark Serialization | calls descending, then implementation/layer/operation/root class ascending |

The default display limit is 50 rows per section. Configure `reporting.human.maxRows` globally and override it with `reporting.human.sections.<section>.maxRows` for `methodProfiling`, `argumentGroups`, `sparkSerialization`, `sparkSerializationDetail`, or `diagnostics`. A value of `0` means unlimited; negative values are invalid. Omitted rows are reported explicitly. These are display limits only and do not change profiler retention or Runtime Statistics API data.

Limits apply equally to in-memory, scope, checkpoint, Markdown, and persisted human reports, including diskless mode.

Rows are collected, sorted by the section's numeric relevance, limited, and only then formatted. Therefore a limited serialization report contains the highest-`CALLS` rows, while a limited method or argument report contains the highest-`INVOCATIONS` rows. The exact omitted count is printed below each truncated table.
