# BUILD-PLAN Gap Audit

## Scope and sources

The previous plan and token traceability were audited directly against the complete updated specification and the independently extracted article draft. LibreOffice was unavailable, so article layout was not visually rendered; complete text extraction succeeded.

## Defects corrected

- Replaced 221 keyword occurrences with 4270 atomic semantic records across MUST, MUST_NOT, REQUIRED, SHOULD, SHOULD_NOT, MAY, table, field, default, test, and acceptance contracts.
- Added 745 article-derived records and separated future-only ideas from 0.1 obligations.
- Corrected configuration resolution to exactly one external source; added explicit/classpath/cwd/default-only/scalar-last/boolean-flag/array/invalid-complex tests.
- Replaced normative profile spark4 with spark42 and recorded the specification inconsistency.
- Replaced the final 8-hour stress gate with the required 24-hour no-growth gate.
- Corrected final filenames and added source-specification normalization/copy and project-archive viewer placement.
- Replaced 29 oversized tasks with 309 focused tasks, including one task per generic feature and focused Throwable, bytecode, bridge, Spark, Py4J, viewer, CI, security, performance, documentation, and release units.
- Replaced broad/wildcard tests with 3790 exact task tests, 27 distinct integration tests, and 8 cumulative packaged suites containing full record metadata.
- Added 274 configuration-key, 24 feature, and 689 output-field inventory records.
- Restored inclusive timing, Throwable lifecycle, serialization byte method/accuracy, layered I/O, missing data, evidence-not-causality, operational workflow, exact opcodes/constructor regression, loader/module/hierarchy, Spark action/storage/Py4J, viewer, CI, performance, and Git evidence contracts.
- Added non-self-referential external CI evidence keyed by committed hash.

## Sequencing corrections

Contracts and metadata precede producers; packaged vertical slice appears in I01; generic core precedes instrumentation; bridge/loader safety precedes bootstrap targets; Spark-neutral SPI precedes adapters; Spark35 precedes Spark42 reuse; stable semantics precede the complete viewer; CI/security/performance arrive with capabilities and remain cumulative.

## Remaining ambiguities and decisions

- Section 7 says spark42 while older section-27 examples say spark4: spark42 selected by explicit revision instruction.
- Article roadmap contains future ideas also made normative by the updated specification: specification requirements are planned; article-only future ideas are recorded as not selected.
- Some table rows describe recommended rather than mandatory values: they are retained as TABLE_CONTRACT or DEFAULT_CONTRACT and implemented for maximum conformance.
- Exact source line ranges refer to the current specification/article extraction and are supplemental; IDs are semantic and stable.

## Coverage comparison

| Measure | Previous | Revised |
|---|---:|---:|
| Traceability units | 221 keyword tokens | 4270 atomic semantic requirements |
| Tasks | 29 | 309 |
| Task tests | 29 mapped placeholders | 3790 executable records |
| Stage tests | 13 broad records | 27 distinct integrations |
| Iteration tests | 6 broad records | 8 cumulative packaged suites |
| Configuration keys | No exhaustive inventory | 274 |
| Output fields | No exhaustive inventory | 689 |
| Article-derived records | Not independently audited | 745 |
| Uncovered mandatory requirements | Claimed zero without semantic proof | 0 after inventory validation |
