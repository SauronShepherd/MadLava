# MadLava

MadLava is a targeted JVM diagnostics agent. Iteration I01 (`0.1.0-dev.1`) is a safe packaged baseline that proves Java-agent startup, bounded asynchronous schema-v3 JSONL output, final shutdown reporting, a runnable workload, and local offline report inspection.

## Build

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
mvn -B -Pgeneric clean verify -Drevision=0.1.0-dev.1
```

## Run the packaged example

```powershell
java -javaagent:target/madlava-agent-0.1.0-dev.1.jar=output=target/demo-output -jar target/madlava-agent-0.1.0-dev.1-example.jar
```

The target application prints `MADLAVA_EXAMPLE_OK=49995000`. MadLava prints one readiness message to stderr and writes `target/demo-output/madlava.jsonl`. Open `report-viewer/index.html` and select that file.

## I01 limitations

I01 provides only the safe reporting vertical slice and `selfObservability` envelope. Configuration discovery, instrumentation, generic JVM collectors, Spark adapters, advanced viewer views, control, dumps, and incident recording arrive in later planned iterations. The report is process-local evidence and does not claim root cause.
# Constructor and Throwable observation

Iteration 03 observation is opt-in. Add `instrumentationInclude=com.example` to the agent arguments to transform only that package prefix. Add `jfrThrowables=true` to request the optional JFR Throwable source; unsupported JVMs report `UNAVAILABLE`. Reports separate successful outermost construction, Throwable creation, explicit `ATHROW`, propagation, and JFR events. Exception messages are never captured.
