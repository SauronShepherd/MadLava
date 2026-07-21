# Getting started

Install a supported JDK and Maven, then run `mvn -B -Pgeneric clean verify -Drevision=0.1.0`. Attach `target/madlava-agent-0.1.0.jar` with `-javaagent`, supplying a writable `output` directory. Start with default low-cost JVM metrics; enable targeted options only for a defined investigation.

```powershell
java -javaagent:target/madlava-agent-0.1.0.jar=output=target/demo-output -jar target/madlava-agent-0.1.0-example.jar
```

Open `report-viewer/index.html`, select `target/demo-output/madlava.jsonl`, and move between snapshots. For a viewer-only tour, select `report-viewer/sample/madlava-sample.jsonl`. Filter features by name or state, inspect diagnostic cards and raw data, and export a compact local summary if needed.

Treat the report as bounded process-local evidence. Confirm suspected behavior with application context and another diagnostic source before changing production code. Remove the agent after the investigation unless continuous observation is intentional and its overhead has been evaluated.
