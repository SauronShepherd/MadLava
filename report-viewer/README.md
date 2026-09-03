# MadLava offline report viewer

Open `index.html` directly in a current browser. Choose one or more MadLava JSONL report segments, or select a complete run directory. No server or installation is needed.

The viewer accepts supported snapshot schemas (v1/v3/v4), method-trace records, and configuration-change records. It orders rotated segments, can discover a run from `madlava-report-manifest.json`, verifies FINAL manifest file inventory/byte counts/SHA-256 hashes, surfaces dropped/degraded/rejected-run warnings, filters raw records by type, correlates timestamped events causally to the latest timestamped snapshot at or before the event, and falls back to stream order when timestamps are unavailable. Exported summaries include event/record totals, record-type counts, and correlation-type counts.

## Temporal correlation contract

`sample/madlava-temporal-correlation.jsonl` exercises the implemented conservative correlation policy:

- parse only valid record `timestamp` values;
- for an event with a valid timestamp, choose the latest snapshot whose valid timestamp is less than or equal to the event timestamp;
- do not associate an event with a future snapshot merely because that snapshot is closer in absolute time;
- if usable timestamp evidence is unavailable, fall back to existing stream-order correlation;
- an out-of-order event timestamp earlier than every timestamped snapshot remains explicitly uncorrelated rather than inheriting stream position.

The raw-record inspector labels timestamp matches, stream-order fallbacks, and explicitly uncorrelated timestamped events separately. This keeps correlation reversible and avoids inventing chronology when producers emit mixed or partially timestamped records.

The viewer performs no network requests, uses no cookies or telemetry, and renders report-controlled values through text-only DOM APIs. It rejects empty input, malformed JSONL with a source line number, unsupported record types/schemas, and invalid manifest integrity. Keep the original JSONL as the authoritative diagnostic record.
