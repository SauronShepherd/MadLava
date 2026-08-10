# Architecture and technical specification

MadLava 0.1.0 is an in-process Java instrumentation agent. The active startup path is `MadLavaAgent` -> `AgentRuntime` + `JsonlReporter` + `MadLavaTransformer`. The older `RuntimeContext` / `FeatureRegistry` / `SnapshotScheduler` / `CompositeTransformer` path remains in the source tree for compatibility/history but is not booted by the current agent and must not be used as the reference architecture for new fixes.

## Active data path

`MadLavaAgent` resolves startup options and the nested JSON configuration, installs the active ASM transformer when method or Spark-serialization observation is enabled, configures the static fail-open bridges, registers `AgentRuntime`, and starts `JsonlReporter`.

Instrumented application threads update bounded in-memory counters. They never write the report file directly. `JsonlReporter` schedules runtime snapshots, encodes them to JSON, and submits them to a bounded queue. `JsonlWriter` is the single background file writer and performs segment rotation plus final integrity-manifest generation. Trace events use a separate bounded `TraceDispatcher`, whose sink feeds the same report queue.

Each run uses a unique directory beneath `output.directory`; a run lock remains held while its writer can still touch the report. `madlava-run-<pid>.json` is published only after the report destination is synchronously openable and the writer lifecycle has started. Hot output rotation preflights and acquires ownership of the new run before releasing the previous run; a pre-start output change relocates the pending destination without implicitly starting I/O.

## Runtime configuration

`RuntimeConfigurationManager` publishes one immutable configuration state at a time. JSON files use the documented nested object schema; literal dotted JSON property names are rejected so startup parsing and hot reload cannot interpret the same file differently. Programmatic reload APIs use canonical flat property names.

Only settings that can be changed coherently at runtime are accepted as live changes. Method filter changes require JVM retransformation support. Settings that would require rebuilding instrumentation, capacities, serializer plans, or scheduling are rejected as restart-required. Listener-side failures such as output-rotation or retransformation failures are surfaced as `APPLIED_WITH_LISTENER_FAILURE`; the current implementation does not provide a fully transactional rollback across all listener side effects.

## Method profiling and tracing

`MadLavaTransformer` instruments selected non-constructor, non-native, non-abstract methods. `MethodFilter` owns admission and exclusions always win. `MethodObservationPlan` controls the measurement mode of methods that have already passed the filter.

Plain rules use aggregate `COUNT`. `Class.method(*)` uses aggregate `COUNT_BY_ARGS`; argument groups are recorded at method entry and contain only bounded, identity-free canonical forms and per-run salted scalar fingerprints. Method call TRACE events are separately sampled and asynchronous. The source tree contains an `ArgumentCapture`/TRACE_ARGS model, but per-invocation TRACE_ARGS is not exposed by the active 0.1.0 configuration and must be treated as dormant rather than supported.

Method IDs are provisionally reserved during transformation and committed only after ASM successfully emits transformed bytes. Failed transformations roll reservations back. Class-loader scopes use weak identity semantics and never invoke application-defined loader `equals()` or `hashCode()`.

## Spark serialization

Spark serializer instrumentation uses an exact `SparkSerializationPlan` and has no production Spark dependency. The bridge suppresses nested serializer operations, attaches each in-flight observation to the configuration generation under which it started, and distinguishes exact ByteBuffer byte counts from unavailable byte evidence. Target-match coverage is committed only after successful class transformation.

## Snapshot/API schemas

There is not one global schema number across every MadLava surface:

- active JSONL `AgentRuntime` snapshots currently use `schemaVersion: 1` and include `final: true` on shutdown;
- checkpoint/delta runtime APIs and method trace events currently use `schemaVersion: 5` with API-specific fields;
- the legacy `SnapshotScheduler` path uses the older schema-v3 model and is not the active agent writer.

Code and documentation must identify the surface before interpreting a schema version.

## Safety and privacy

All hot-path registries and queues are bounded or weakly referenced where lifecycle identity is required. Application payloads, exception messages, return values, and raw method arguments are not retained by active aggregate profiling. `COUNT_BY_ARGS` stores bounded canonical shapes/fingerprints only. Report JSON encoding avoids arbitrary application `toString()` calls for unknown values.

Bridge implementations catch internal failures, but the stronger guarantee that an injected bridge invocation can never fail at JVM linkage/class-initialization time still requires bytecode-level protection or a deliberately bootstrap-visible bridge. Bootstrap classes are therefore not instrumented by the active transformer today.

## Diagnostics and dormant components

Low-cost JVM MXBean metrics are active. Undefined MXBean values are represented as unavailable/partial rather than fabricated zeros. `DiagnosticsRuntime`, the legacy Spark observation registry, JFR throwable hooks, `SnapshotScheduler`, and `CompositeTransformer` contain retained compatibility/experimental code; they are not all wired into active startup. Their presence in the source tree is not evidence that the corresponding public feature is enabled.

## Release artifacts

The Maven build targets Java 11, shades and relocates ASM, and declares both `Premain-Class` and `Agent-Class`. The manifest requests class retransformation (`Can-Retransform-Classes: true`) because live method-filter reload depends on it when the JVM supports retransformation.

## Scope boundary

Measurements are process-local observational evidence. MadLava does not promise causal attribution, globally complete distributed Spark state, physical I/O accounting from wrapper counts, or zero overhead. Cumulative counters use concurrent adders and are not a globally transactional snapshot across every field. Named scopes are checkpoint deltas and report configuration changes explicitly when a reload crosses the interval.
