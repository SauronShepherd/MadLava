# Profiling scopes

Use `MadLavaScopes` for a named profiling interval. Scopes use the existing checkpoint/delta engine internally and do not create Spark objects.

```java
String scope = MadLavaScopes.beginScope("kryo");
runWorkload();
String result = MadLavaScopes.endScope(scope);
System.out.println(MadLavaReport.scopeReportText(result));
```

The equivalent PySpark flow is:

```python
scopes = spark.sparkContext._jvm.com.madlava.api.MadLavaScopes
reports = spark.sparkContext._jvm.com.madlava.api.MadLavaReport
scope = scopes.beginScope("kryo")
run_workload()
result = scopes.endScope(scope)
print(reports.scopeReportText(result))
```

`scopeResultJson(resultId)` returns the immutable completed result. Results are retained with bounded capacity and can be released early with `releaseScopeResult(resultId)`. Scope IDs and result IDs are opaque; repeated and overlapping scope names are supported.

Invalid or unavailable operations return a structured JSON error, including `INVALID_SCOPE_NAME`, `UNKNOWN_SCOPE_OR_ALREADY_ENDED`, and `UNKNOWN_OR_EXPIRED_SCOPE_RESULT`.

Use checkpoints directly when you need low-level baselines or multiple independent statistics queries. Scopes are the preferred API for named application or notebook experiments.
