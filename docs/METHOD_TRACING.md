# Generic method tracing

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
- No argument, return-value, payload, or exception-message retention.
- No report file I/O on application threads.
- Callback failures never replace application failures.
- MadLava and ASM classes are excluded from instrumentation.

## Timing

`System.nanoTime()` measures inclusive elapsed time. Parent and child method durations overlap by design and must not be summed as independent wall-clock time.
