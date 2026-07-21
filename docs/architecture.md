# Architecture and technical specification

MadLava 0.1.0 is an in-process Java instrumentation agent with a deliberately small failure domain. The target JVM loads `MadLavaAgent` through `premain` or `agentmain`. Bootstrap creates an immutable `RuntimeContext`, feature registry, bounded snapshot queue, scheduled collector, and asynchronous JSONL writer. Any bootstrap failure is caught and reported to stderr rather than propagated into the application.

## Data path

Low-cost JMX collectors and optional observation bridges update bounded, concurrency-safe aggregates. Instrumented application threads update counters only; they do not perform report disk I/O. `SnapshotScheduler` serializes a point-in-time view into schema-v3 data and submits it to `BoundedSnapshotQueue`. `JsonlWriter` drains the queue on its daemon thread. Queue pressure is visible through drop counters, and shutdown requests a final snapshot.

Optional ASM transformations are limited by `instrumentationInclude`. Constructor and throwable sources remain distinct. Runtime I/O keeps wrapper layers separate, serialization deduplicates nested operations, and executor metrics use weak lifecycle tracking. Expensive diagnostics are disabled by default and exposed through a local platform MBean when enabled. Spark probing uses reflection so Spark and Scala are not production dependencies.

## Safety and privacy

Collections, histories, strings, queues, and entity registries have explicit bounds. Weak references prevent diagnostic bookkeeping from retaining observed objects. The report intentionally excludes payloads, object contents, method arguments and returns, exception messages, secrets, full paths, Python tracebacks, and Spark records. Endpoints and entity identities are anonymous. Capability states communicate `RUNNING`, `DEGRADED`, `UNAVAILABLE`, or unsupported combinations instead of inventing measurements.

The report viewer is static HTML, CSS, and JavaScript. Its Content Security Policy disables network connections and object content. Report-controlled values are rendered with `textContent`; the viewer requires no server and performs no telemetry.

## Artifact set

The Maven release produces the shaded Java 11 agent JAR, example JAR, offline viewer ZIP, and complete-project ZIP. ASM is relocated inside the agent. The JAR manifest declares both startup and dynamic-attach entry points and does not request class retransformation.

## Scope boundary

Measurements are process-local observational evidence. MadLava does not promise causal attribution, globally complete distributed Spark state, physical I/O accounting from wrapper counts, or zero overhead. When a runtime signature or source is unavailable, the affected capability must degrade independently and preserve the application.
