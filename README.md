# MadLava - Feel the Lava

MadLava 0.1.0 is a lightweight Java agent for targeted, process-local JVM diagnostics. It writes bounded schema-v3 JSONL snapshots asynchronously and includes a dependency-free offline report viewer. Optional probes cover JVM metrics, selected constructor and throwable activity, layered I/O and serialization, executor lifecycles, bounded diagnostics, and dependency-free Spark runtime observation.

The story behind the project and artifact is in the Towards Data Engineering article ["MadLava: Feel The Lava"](https://medium.com/towards-data-engineering/madlava-feel-the-lava-ac32d4be8ad4).

## Built with Codex and GPT-5.6

MadLava was designed and delivered with Codex and GPT-5.6 using an evidence-gated iteration model. The specification was decomposed into eight versioned iterations; each iteration contains integrated stages, and each stage contains independently scoped tasks with positive and negative acceptance contracts. Work starts on a dedicated `Iteration-<number>` Git branch. Task tests feed stage integration tests, which feed iteration and cumulative regression gates. A branch is eligible for its final commit and push only after its required tests, packaging checks, compatibility lanes, artifact validation, and certification pass. The exhaustive mapping and acceptance criteria are preserved in [BUILD-PLAN.md](BUILD-PLAN.md).

This workflow used GPT-5.6 for architectural decomposition, implementation, failure analysis, documentation, and test design, while Codex operated the repository: creating iteration branches, editing code, running the gates, inspecting artifacts, committing accepted work, pushing it, and verifying the remote commit hash.

## Architecture

MadLava runs inside the observed JVM:

1. `MadLavaAgent` parses startup options and creates an immutable runtime context.
2. Optional ASM instrumentation and runtime bridges record only selected, bounded events.
3. Collectors aggregate metrics in memory; weak references avoid retaining observed application objects.
4. A daemon scheduler creates snapshots and submits them to a bounded, non-blocking queue.
5. A daemon writer emits UTF-8 schema-v3 JSONL. Application threads do not write report files.
6. The offline viewer reads the JSONL locally and renders report-controlled values through text-only DOM APIs.

Agent bootstrap is fail-safe: observation failures disable or degrade the relevant capability instead of changing application behavior. See [architecture](docs/architecture.md) for component and privacy details.

## Requirements and supported platforms

- Maven 3.9 or newer to build from source.
- Java 11, 17, or 21 for generic JVM observation. The artifact targets Java 11 bytecode.
- Spark is not bundled. Version 0.1 uses runtime reflection and currently recognizes Spark 3.5.x Classic on Java 11 or 17. Other Spark/JDK combinations report an explicit unsupported or unavailable state.
- The report viewer needs a current browser with `File.text()`, `Blob`, and object URL support. It runs directly from disk and needs no web server or network connection.
- Windows, Linux, and macOS are supported wherever a compatible JDK can load `-javaagent`. Release automation in this repository is PowerShell-based.

## Install and build

Download or build `madlava-agent-0.1.0.jar`; no application dependency changes are required.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-11'
mvn -B -Pgeneric clean verify -Drevision=0.1.0
```

The build produces the agent JAR, runnable example JAR, offline viewer ZIP, and complete-project ZIP under `target/`.

Attach MadLava when starting an application:

```powershell
java -javaagent:target/madlava-agent-0.1.0.jar=output=target/madlava-output -jar application.jar
```

Common opt-in options are comma-separated:

```text
output=target/madlava-output,
instrumentationInclude=com.example,
jfrThrowables=true,
runtimeObservation=true,
diagnostics=true,
sparkObservation=true
```

Keep `instrumentationInclude` narrow. Expensive diagnostics and all instrumentation are disabled unless requested. Output defaults to `madlava-output/madlava.jsonl`.

## Run the packaged example

```powershell
java -javaagent:target/madlava-agent-0.1.0.jar=output=target/demo-output -jar target/madlava-agent-0.1.0-example.jar
```

The application prints `MADLAVA_EXAMPLE_OK=49995000`; MadLava writes `target/demo-output/madlava.jsonl` and a final shutdown snapshot.

## Use the report viewer

Open [report-viewer/index.html](report-viewer/index.html) directly in a browser, then choose either your generated `madlava.jsonl` or the bundled [sample report](report-viewer/sample/madlava-sample.jsonl). The viewer offers snapshot navigation, feature filtering, health and diagnostics cards, raw snapshot inspection, and local summary export.

Reports stay inside the browser: the viewer has no external assets, server, telemetry, cookies, or network requests. It rejects malformed JSON and unsupported schema versions with the offending line number. See the [viewer guide](report-viewer/README.md).

## Testing and certification

Run the full generic build and tests:

```powershell
mvn -B -Pgeneric clean verify -Drevision=0.1.0
```

Run the final release certification, which executes supported JDK lanes, cumulative tests, packaged-agent checks, bytecode and manifest inspection, viewer/security checks, archive validation, documentation checks, and checksums:

```powershell
pwsh -File scripts/certify-i08.ps1 -Revision 0.1.0
```

For the committed-HEAD release gate:

```powershell
pwsh -File scripts/certify-i08.ps1 -Revision 0.1.0 -CommittedHead
```

Testing details and lane expectations are documented in [docs/testing.md](docs/testing.md).

## Version 0.1 limitations

- MadLava reports bounded observational evidence, not a guaranteed root-cause diagnosis or a replacement for a profiler, tracing platform, Spark UI, or heap analyzer.
- All measurements are process-local; executor-wide Spark visibility requires deploying an agent to each relevant JVM.
- Instrumentation observes only explicitly included classes. Counts can therefore be partial and are labelled by source and accuracy.
- Wrapper-layer I/O can overlap; it is not claimed as physical device or network traffic.
- Payloads, exception messages, full paths, arguments, return values, secrets, Python tracebacks, and Spark records are intentionally not captured.
- Heap dumps are never automatic. Availability depends on an explicitly configured provider.
- Tables, queues, histories, strings, and weak entity registries are bounded; overload can cause documented drops or truncation.
- Spark support is reflective and deliberately conservative. Spark 4/PySpark certification is not claimed by the 0.1 implementation.
- The viewer is an offline investigation aid and does not execute queries or modify a running JVM.

## What's next

Likely post-0.1 work includes certified Spark 4 and PySpark adapters, broader compatibility automation on Linux and macOS, richer time-series comparisons and correlations in the viewer, generated configuration/output-field reference pages, signed release provenance and SBOM publication, and longer representative performance/no-growth campaigns.

## Further documentation

- [Getting started](docs/getting-started.md)
- [Architecture and technical specification](docs/architecture.md)
- [Testing and release gates](docs/testing.md)
- [Offline viewer guide](report-viewer/README.md)
- [Changelog](CHANGELOG.md)
- [Exhaustive build plan](BUILD-PLAN.md)
