# Generic method tracing

> **Active 0.1.0 boundary:** aggregate `COUNT`/`COUNT_BY_ARGS` and sampled non-argument
> TRACE events are wired into the active agent. The `ArgumentCapture` / per-invocation
> `TRACE_ARGS` model present in source is dormant and has no supported configuration key.


## Observation modes and argument safety

Plain `Class.method` rules are cheap `COUNT` profiling. The explicit
`Class.method(*)` suffix represents aggregate `COUNT_BY_ARGS` and is not a
method-name wildcard. It groups bounded rendered argument tuples and does not
emit one event per invocation. JVM descriptors can be appended with `#` to
distinguish overloads.

Argument rendering is opt-in and bounded. `SAFE` never invokes application
`toString()` for arbitrary objects; `TO_STRING` is explicit and catches failures.
`COUNT_BY_ARGS` does not retain scalar literals: strings, numbers, booleans,
characters, and enum constants use a per-run salted fingerprint so equal values
still group together inside one JVM run without exposing the original literal.
Sampling is independent from aggregate counting, so a zero trace sample rate
still preserves exact invocation metrics. Redaction supports zero-based argument
indexes and compiled value patterns.

The parser, renderer, sampler, redactor, atomic configuration manager, file
watcher, live writer switching, streamed method-call records, and selective
`COUNT_BY_ARGS` aggregation are implemented. Equal rendered argument tuples
share one bounded aggregate counter rather than producing one event per call.

COUNT_BY_ARGS uses a separate identity-free canonical grouping representation:
arbitrary objects group by fully qualified runtime class name, arrays by component
type and length, and lambda/hidden-class names drop `/0x...` and instance identity
suffixes while preserving the lambda ordinal such as `$$Lambda$4243`. This is
intended for within-JVM-run aggregation; lambda ordinals are not guaranteed to
be stable across separate JVM runs.

## Contract

For every selected non-constructor, non-native, non-abstract method, MadLava records:

- invocation count;
- normal completion count;
- exceptional completion count when a `Throwable` escapes the method;
- inclusive total, minimum, maximum, and average duration;
- owner, method name, JVM descriptor, and class-loader scope.

A throw that is caught inside the original method is not an exceptional method completion.

## Transformation shape

Conceptually:

```java
long started = MethodProbeBridge.enter(methodId);
try {
    Result result = originalMethod();
    MethodProbeBridge.normalExit(methodId, started);
    return result;
} catch (Throwable failure) {
    MethodProbeBridge.exceptionalExit(methodId, started, failure);
    throw failure;
}
```

The exact original return value and exact original `Throwable` object remain on the application path.

## Filtering

```text
methodProfiling=true
methodInclude=com.example.Service.*;org.apache.spark.serializer.KryoSerializerInstance.serialize
methodExclude=com.example.Service.noisy
methodMaxEntries=2048
```

Descriptor-specific selection:

```text
com.example.Service.execute#(Ljava/lang/String;)V
```

Excludes win. Empty includes select nothing.

## Safety

- Thread-local callback recursion guard.
- Bounded registry with dropped-registration reporting.
- No raw argument, return-value, payload, or exception-message retention; `COUNT_BY_ARGS` keeps only bounded type shapes and per-run salted scalar fingerprints.
- No report file I/O on application threads.
- Bridge-internal callback failures are fail-open. JVM linkage/class-initialization failure at an injected call site remains a known hardening item; bootstrap classes are not instrumented.
- MadLava and ASM classes are excluded from instrumentation.

## Timing

`System.nanoTime()` measures inclusive elapsed time. Parent and child method durations overlap by design and must not be summed as independent wall-clock time.
