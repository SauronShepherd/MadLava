# Spark serialization feature

## Supported Spark lines

The adapter uses exact JVM descriptors shared by Spark 3.5 and Spark 4 serializer implementations.

- Spark 3.5.x: Scala 2.12 and 2.13.
- Spark 4.x: Scala 2.13.

Iteration-12 pins automated proof to Spark 3.5.9, 4.0.4, 4.1.3, and 4.2.0. These pins are certification points, not hardcoded runtime version checks. A later patch release in a supported line remains eligible when its exact targets match; unmatched signatures are visible in coverage rather than being instrumented fuzzily.

## Purpose

The feature answers:

- Which Spark serializer implementation ran?
- Which serialization boundary ran?
- How often did it complete normally or exceptionally?
- How much inclusive time was observed?
- Which bounded root-class name crossed the selected boundary?
- How many bytes were exposed by an exact `ByteBuffer` boundary?
- Which Spark and Scala runtime were detected?
- Were the expected classes and exact descriptors transformed?

It does not infer deep object size, physical network traffic, shuffle-file size, or cross-record deduplication from method calls alone.

## Targets

### Boundary layer

```text
JavaSerializer.newInstance
JavaSerializerInstance.serialize
JavaSerializerInstance.deserialize
JavaSerializerInstance.serializeStream
JavaSerializerInstance.deserializeStream
KryoSerializer.newInstance
KryoSerializerInstance.serialize
KryoSerializerInstance.deserialize
KryoSerializerInstance.serializeStream
KryoSerializerInstance.deserializeStream
```

### Stream layer

```text
JavaSerializationStream.writeObject
JavaDeserializationStream.readObject
KryoSerializationStream.writeObject
KryoDeserializationStream.readObject
```

Targets include exact JVM descriptors and Scala `ClassTag` erasure. Scala 2.12 and 2.13 use the same erased `scala.reflect.ClassTag` descriptor at these boundaries.

## Runtime identity

The report performs best-effort, dependency-free reflection against Spark's and Scala's package objects at snapshot time. It emits:

```json
{
  "sparkVersion": "4.2.0",
  "scalaVersion": "2.13.x",
  "javaVersion": "21.x",
  "supported": true
}
```

Detection failures become `unknown`; they never fail the application.

## Activation

```text
sparkSerialization=true
sparkSerializationProfile=ALL
sparkSerializationRootClasses=true
sparkSerializationMaxGroups=2048
```

No application wrapper call is required.

## Byte semantics

| Operation | Measurement | Accuracy |
|---|---|---|
| `serialize` success | returned `ByteBuffer.remaining()` | `EXACT_RETURNED_BYTEBUFFER` |
| `deserialize` success | input `ByteBuffer.remaining()` before invocation | `EXACT_INPUT_BYTEBUFFER` |
| stream read/write | no universal exact position | `UNAVAILABLE` |
| factory calls | no payload boundary | `UNAVAILABLE` |
| failed operations | no successful payload boundary | `UNAVAILABLE` |

`UNAVAILABLE` is not zero.

## Nesting and overlap

Boundary implementations may invoke selected stream methods internally. Nested operations on the same thread are suppressed and attributed to `nestedOperationsSuppressed`. Boundary and stream profiles are still separate observational layers and must not be summed as independent physical traffic.

## Privacy and safety

The bridge retains only bounded class names and aggregate counters. It does not retain objects, fields, serialized bytes, return values, exception messages, or stack traces. Callback failures are swallowed and counted; application behaviour wins.
