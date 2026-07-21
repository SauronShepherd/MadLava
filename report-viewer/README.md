# MadLava offline report viewer

Open `index.html` directly in a current browser. Choose a MadLava schema-v3 JSONL report with the file picker; no server or installation is needed.

To explore immediately, choose `sample/madlava-sample.jsonl`. The snapshot selector moves through the report timeline. The search box filters feature rows by name or state. Overview cards show version, sequence, final-snapshot state, dropped snapshots, and healthy features. Diagnostic cards summarize available JVM or Spark sections, while **Raw selected snapshot** exposes the complete selected record. **Export summary** downloads a smaller JSON summary locally.

The viewer performs no network requests, uses no cookies or telemetry, and renders report-controlled values through text-only DOM APIs. It rejects empty input, malformed JSONL with a line number, and schemas other than version 3. Keep the original JSONL as the authoritative diagnostic record.
