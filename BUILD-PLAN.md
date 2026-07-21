# MadLava 0.1.0 Build Plan

**Plan ID:** `ML-BP-0.1.0`
**Specification:** `MadLava-0.1.0-Complete-Technical-Specification-Updated.md`, revision 2026-07-21
**Prepared:** 2026-07-21
**Repository baseline:** empty, uncommitted source tree on planning branch `Iteration-01`
**Target:** `0.1.0`; plan versions: `0.1.0-dev.1`, `0.1.0-alpha.1`, `0.1.0-alpha.2`, `0.1.0-beta.1`, `0.1.0-rc.1`, `0.1.0`.

## Executive summary

This is an implementation-ready, dependency-ordered plan for building MadLava from an empty repository. It converts the normative specification into six vertical, installable increments. Each increment produces a real Java-agent JAR, a runnable child-JVM demonstration, validated JSONL, an offline viewer capable of opening that iteration's output, documentation, and cumulative certification evidence. The final increment supplies the complete generic JVM and Spark/PySpark product, viewer, archives, checksums, SBOM/provenance, and compatibility matrix.

## Planning principles

- The hierarchy is **Build Plan → Iterations → Stages → Tasks → Task-level tests**. A task is the smallest independently implementable unit; each task leaves `mvn verify` green.
- The specification wins over repository state and historical repository claims. Public vocabulary uses “feature”; all shipped text is English.
- One Maven module, one user-facing agent JAR, Java 11/class-file 55, no Spark/test dependencies in that JAR, relocated public ASM, at most one primary transformer.
- Hot paths perform no blocking, file I/O, JSON encoding, sorting, unbounded capture, or global locking. All queues, maps, samples, retained details, dumps, and files are bounded.
- Every non-exact value exposes accuracy, coverage, availability, truncation, and degradation. Privacy-safe defaults capture no values, payloads, messages, credentials, literals, full paths, or full endpoints.
- Each iteration is certified from packaged artifacts. Mandatory tests cannot be skipped. A failed lane, artifact check, example, commit, push, or remote-hash check leaves the iteration incomplete.

## Source-of-truth and conflict-resolution rules

The updated specification is normative. Its article-derived semantics govern interpretation; its source-tree mapping is guidance only. Existing behavior marked as a conformance gap must be replaced. Post-0.1 ideas in section 40 are excluded except foundations explicitly required elsewhere. `BUILD-PLAN-TRACEABILITY.json` contains one stable record for every source line containing a normative keyword, including multiple keyword occurrences on the same line, and maps each record to tasks and tests.

## Repository assessment

| Area | Verified fact | Conclusion / recommendation |
|---|---|---|
| Structure | Only the 145,742-byte specification and untracked `.idea` metadata exist; `git ls-files` is empty. | Create the normative single-module tree; do not import assumptions from a missing historical project. |
| Build | No `pom.xml`, wrapper, source, or script exists; no build command can run. | Current build status is **not runnable**, not “failing.” Bootstrap Maven in I01. |
| Implementation | No Java, resource, viewer, or configuration files exist. | Every product capability is missing. |
| Tests | No test sources, fixtures, profiles, or CI exist. | Establish JUnit/Failsafe/child-JVM/schema testing before feature growth. |
| Artifacts | No JAR, report, viewer ZIP, archive, checksum, SBOM, or provenance exists. | Artifact inventory is empty. |
| Documentation | Only the normative specification exists. | README, guides, governance, examples, compatibility, privacy, and interpretation docs are missing. |
| Reusable items | The specification is complete and authoritative. | Reuse its configuration tables, schema contract, fixtures, matrix pins, and semantics as generated/test inputs. |
| Replace vs extend | No product code exists. `.idea/workspace.xml` is user-local state. | Do not extend or commit IDE workspace state; create a root `.gitignore`. |
| Source-tree discrepancy | Section 7/38 describes a “latest project,” but none is present. | Treat section 38 baseline claims as unavailable historical evidence and implement all conformance improvements. |
| Assumptions | No Maven/JDK availability was established during plan authoring; remote `origin` exists but `origin/main` does not. | Commands below are certification contracts, not claims that the absent project already passes. |

### Gap analysis and high-risk areas

All required product, build, packaging, tests, viewer, documentation, CI, security, compatibility, and release work is missing. Highest risks are constructor/exception bytecode semantics, bootstrap/module/class-loader isolation, bounded concurrent aggregation, application-thread overhead, privacy leakage, Spark internal signature drift and Scala lines, Py4J lifecycle correlation, schema evolution, offline-browser security, and reproducibility. These are introduced behind fail-safe contracts and receive negative tests before user-visible enablement.

## Architectural dependency map

```text
Maven + schemas + config
  -> lifecycle + feature framework + bounded aggregation
  -> scheduler/writer + baseline polling + minimal viewer
  -> transformer + bootstrap bridge + deep generic callbacks
  -> dumps/sampling/control/incident/overhead
  -> Spark-neutral adapter SPI -> Spark 3.5 -> Spark 4/PySpark
  -> stable schema + full viewer -> hardening + final release
```

No stage consumes a contract from a later stage. Output schema precedes producers and viewer consumers; aggregation precedes callbacks; bridge safety precedes JDK/bootstrap instrumentation; generic core precedes Spark adapters.

## Proposed iteration/version roadmap

| Iteration | Version | User-visible increment | New mandatory lanes |
|---|---|---|---|
| I01 | 0.1.0-dev.1 | Packaged agent, configuration, safe lifecycle, baseline JVM report, minimal offline viewer | Generic JDK 11 |
| I02 | 0.1.0-alpha.1 | Selective instrumentation and deep generic I/O/serialization/throwable profiling | Generic JDK 17, 21 |
| I03 | 0.1.0-alpha.2 | Sampling, dumps, control, incidents, adaptive overhead and resource diagnostics | Generic 11/17/21 stress/performance |
| I04 | 0.1.0-beta.1 | Spark 3.5.9 Classic driver insights | Spark 3.5.9 + JDK 11/17, Scala 2.12 |
| I05 | 0.1.0-rc.1 | Spark 4.2.0, PySpark, complete Spark failures and correlation | Spark 4.2.0 + JDK 17/21, Scala 2.13; PySpark 3.5.9/4.2.0 |
| I06 | 0.1.0 | Complete offline viewer, hardening and reproducible release | All prior lanes, viewer browser/no-network lanes |

# Build Plan

## Iterations

## I01 — 0.1.0-dev.1: Safe baseline JVM agent

**MVP capability added:** launch a real `-javaagent`, merge/validate configuration, collect low-cost JVM state, asynchronously write schema-v3 JSONL, and inspect it offline.
**End-user value:** a safe, no-server JVM health timeline.
**Included stages:** I01-S01 through I01-S03.
**Supported scenarios:** generic Java 11 application startup/shutdown; disabled agent; invalid-config fallback.
**Distribution contents:** `target/madlava-agent-0.1.0-dev.1.jar`, `target/madlava-report-viewer-0.1.0-dev.1.zip`, example config/app, schema and docs.
**Build and packaging commands:** `mvn -B -Pgeneric clean verify` then `mvn -B -Drevision=0.1.0-dev.1 package`.
**Launch and demonstration procedure:** `java -javaagent:target/madlava-agent-0.1.0-dev.1.jar=config=madlava.json.example -jar target/example-app.jar`; unzip viewer and open `index.html`, then select generated JSONL.
**Expected output:** readiness on stderr, UTF-8 JSONL with monotonic sequence, configuration hash, runtime, feature envelopes, final snapshot; viewer overview without network access.
**Documentation delivered:** getting started, configuration precedence, baseline feature semantics, privacy defaults, limitations, troubleshooting.
**Compatibility matrix:** JDK 11 required; JDK 17/21 smoke-only until I02.
**Known limitations:** no bytecode features, dumps, incidents, Spark, or advanced viewer charts.
**Cumulative regression inventory:** config unit/contract, aggregation concurrency, lifecycle isolation, schema, packaged child-JVM, rotation/writer failure, bytecode-55, artifact-content, viewer parser/no-network smoke.
**Iteration-level certification scenarios:** I01-IT01 `mvn -B -Pgeneric clean verify`; I01-IT02 `scripts/verify-java11-bytecode.sh target/*.jar`; I01-IT03 `scripts/verify-packaged-agent.sh`; I01-IT04 `scripts/verify-viewer.sh`; I01-IT05 second clean build hash comparison.
**Iteration release gate:** all I01 task/stage/iteration tests pass from clean checkout; no skipped mandatory test; packaged behavior matches tests.
**Artifact acceptance checklist:** manifest has `Premain-Class`; no Spark/test dependencies; relocated ASM only when introduced; schema validates every line; viewer has no external request; checksums recorded.

### I01-S01 — Build, contracts, and configuration

**Stage objective:** establish the one-module Java-11 build and public configuration/schema contracts.
**User-visible or architectural outcome:** deterministic build, strict validated configuration, English diagnostics.
**Included tasks:** I01-S01-T01..T03. **Prerequisites:** None.
**Stage-level integration scenarios:** resolve embedded/classpath/cwd/explicit/arg sources, recursively merge objects and replace arrays, validate/redact/hash, package and inspect.
**Stage test suite:** I01-S01-ST01 `mvn -B -Dtest=ConfigurationResolutionIT test` expects precedence/error policies; I01-S01-ST02 `mvn -B -Pgeneric package && scripts/inspect-agent-jar.sh` expects class major 55, valid manifest, no forbidden deps.
**Stage acceptance gate:** both tests green and clean rebuild reproducible. **Artifacts or documentation produced:** `pom.xml`, schemas/defaults, example config, build scripts. **Risks:** dependency leakage and ambiguous merge semantics.

#### I01-S01-T01 — Bootstrap the Maven and release skeleton
**Objective**
Create the single Maven module, Java-11 compiler/reproducibility settings, manifest, dependency convergence, profiles, standard tree and governance files.
**Specification references**
Sections 3, 5–7, 26–28, 31, 41.
**Current repository state**
No build or tracked source exists.
**Dependencies**
None.
**Implementation steps**
1. Add root POM with pinned plugins, JUnit Jupiter, Surefire/Failsafe, Shade and reproducible timestamps.
2. Add `Premain-Class`, retransformation flags, enforcer bans for Spark/test runtime leakage, and `generic`, `spark35`, `spark4` profiles.
3. Add Maven/toolchain examples, scripts, `.gitignore`, licence/governance/docs skeleton, and CI generic lane.
**Expected code and file changes**
- `pom.xml`, `.mvn/toolchains.xml.example`, `scripts/*`, `.github/*`, root governance files, `src/{main,test,spark-test}`.
**Behavioral contract**
- Build emits one attachable agent JAR targeting major 55; deterministic inputs yield identical bytes; no source-tree resource is required at runtime.
**Task-level tests**
- `I01-S01-T01-UT01` packaging: run `mvn -B -Pgeneric clean package`; JAR exists and manifest is valid (`scripts/inspect-agent-jar.sh`), new.
- `I01-S01-T01-UT02` dependency/security: `mvn -B dependency:tree` plus archive scan; no Spark, test, duplicate, unrelocated public ASM package, new.
- `I01-S01-T01-UT03` bytecode: scan every class major; all are 55, new.
**Acceptance criteria**
- [ ] Clean build succeeds twice with identical JAR hash. [ ] English-only source check passes. [ ] One Maven module and one agent artifact.
**Completion evidence**
- Maven logs, dependency tree, manifest dump, class-version report, SHA-256 pair.
**Risks and notes**
- Pin plugin/dependency versions centrally; Spark profiles affect tests only.

#### I01-S01-T02 — Define configuration model and merge pipeline
**Objective**
Implement typed defaults, source discovery, argument parsing, recursive object merge/array replacement, strict validation, error policies, redaction and effective hash.
**Specification references**
Sections 10, 20, 24, 33.
**Current repository state**
No parser/model exists.
**Dependencies**
I01-S01-T01.
**Implementation steps**
1. Create immutable configuration DTOs and embedded `madlava-defaults.json` matching every public property/default.
2. Implement precedence: embedded < cwd < classpath < explicit file < scalar args, duplicate/unknown/type/range checks, and three failure policies.
3. Canonicalize, redact secret-pattern keys, hash SHA-256, expose source description and optional owner-protected effective file.
**Expected code and file changes**
- `com.madlava.config.*`, defaults/example, `src/test/.../config/*`.
**Behavioral contract**
- Inputs are UTF-8 JSON and escaped agent args; output is immutable validated config; explicit invalid paths are visible; path traversal is rejected; secrets never enter diagnostics/hash evidence in clear text; concurrent readers need no locks.
**Task-level tests**
- `I01-S01-T02-UT01` parameterized precedence/merge/escaping test, `mvn -B -Dtest=ConfigurationMergeTest test`, new; asserts object recursion and array replacement.
- `I01-S01-T02-UT02` invalid duplicate/unknown/type/range and each error policy, `...ConfigurationValidationTest`, new; asserts deterministic diagnostics/fallback/abort-agent-only behavior.
- `I01-S01-T02-UT03` redaction/hash/path traversal, `...ConfigurationSecurityTest`, new; asserts stable hash and no secret/path escape.
**Acceptance criteria**
- [ ] Every section-24 property is typed/defaulted/tested. [ ] Unknown properties fail in strict mode. [ ] Invalid config cannot terminate target JVM under default policy.
**Completion evidence**
- Parameterized reports, golden canonical JSON/hashes, security-test logs.
**Risks and notes**
- Generate docs/schema from the same property metadata to prevent drift.

#### I01-S01-T03 — Define schema-v3 and compatibility fixtures
**Objective**
Create authoritative JSON Schema, DTO/envelope contracts, units/availability/accuracy vocabulary and golden compatibility corpus.
**Specification references**
Sections 17, 25, 35, 37.3.
**Current repository state**
No schema or report exists.
**Dependencies**
I01-S01-T02.
**Implementation steps**
1. Define root, metadata, feature envelope, self-observability, artifact references and nullable availability fields.
2. Enforce unit suffixes and accuracy enum; document additive schema evolution and reject unsupported future major versions safely.
3. Add valid, partial, truncated, degraded, malformed and future-version fixtures.
**Expected code and file changes**
- `src/main/resources/schema/madlava-report-v3.schema.json`, reporting DTOs, `src/test/resources/reports/*`.
**Behavioral contract**
- Every snapshot contains schema/version/config hash/sequence/time; unavailable is null plus reason; non-exact values cannot omit accuracy; parser errors identify line without executing content.
**Task-level tests**
- `I01-S01-T03-UT01` schema contract/golden validation, `mvn -B -Dtest=ReportSchemaContractTest test`, new.
- `I01-S01-T03-UT02` units/accuracy mutation tests, same command, new; every invalid mutation rejected.
**Acceptance criteria**
- [ ] All fixtures deterministic. [ ] Schema supports history and atomic-latest. [ ] Semantics never imply causality or false precision.
**Completion evidence**
- Validator reports and fixture hashes.
**Risks and notes**
- Schema version changes require backward viewer fixtures from this task onward.

### I01-S02 — Lifecycle, aggregation, output, and baseline features
**Stage objective:** deliver safe runtime collection and bounded asynchronous reporting.
**User-visible or architectural outcome:** useful JVM snapshots with isolated feature failures.
**Included tasks:** I01-S02-T01..T04. **Prerequisites:** I01-S01.
**Stage-level integration scenarios:** premain startup-to-final-shutdown; one feature fails/times out while others report; queue/file failures degrade; concurrent counters snapshot.
**Stage test suite:** I01-S02-ST01 `mvn -B -Dtest=AgentLifecycleIT,FailureIsolationIT verify`; I01-S02-ST02 `mvn -B -Dtest=WriterPipelineIT,AggregationRaceIT verify`.
**Stage acceptance gate:** no application exception/deadlock, bounded memory/queue/files, valid final report. **Artifacts or documentation produced:** runnable agent and JSONL. **Risks:** shutdown races and weak snapshot consistency.

#### I01-S02-T01 — Implement fail-safe lifecycle and feature framework
**Objective**
Implement premain contexts, state machine, descriptors, ordered startup/shutdown, per-feature circuit breaking and independent failure envelopes.
**Specification references**
Sections 8, 9, 11, 12.
**Current repository state**
Only contracts from I01-S01 exist.
**Dependencies**
I01-S01-T02, I01-S01-T03.
**Implementation steps**
1. Add `MadLavaAgent`, immutable contexts, feature registry/descriptors and public states.
2. Isolate initialize/start/snapshot/reconfigure/stop with counters/history/timeouts and reverse shutdown.
3. Add stderr logger with levels/categories, rate limits and readiness/fatal exceptions to OFF.
**Expected code and file changes**
- `com.madlava.agent`, `core`, `runtime`, logging utility and lifecycle tests.
**Behavioral contract**
- Agent catches all bootstrap failures and returns; feature failure affects only that feature; repeated failures open its breaker; stop is bounded/idempotent; logs are English/rate-limited and never use app logging.
**Task-level tests**
- `I01-S02-T01-UT01` lifecycle transitions/order/idempotence, `mvn -B -Dtest=FeatureLifecycleTest test`, new.
- `I01-S02-T01-UT02` initialize/snapshot/stop failures and timeout, `...FeatureFailureIsolationTest`, new; other feature remains RUNNING.
- `I01-S02-T01-UT03` logger reentrancy/rate limit/OFF readiness, `...InternalLoggerTest`, new.
**Acceptance criteria**
- [ ] Every state transition is legal/history bounded. [ ] No error reaches app. [ ] No transformer registered for polling-only config.
**Completion evidence**
- State-transition reports, captured stderr, child-JVM exit codes.
**Risks and notes**
- Timeout cancellation must not interrupt application threads.

#### I01-S02-T02 — Implement bounded concurrent aggregation primitives
**Objective**
Provide counters, atomic min/max, interval deltas, striped bounded tables and immutable weakly consistent snapshots.
**Specification references**
Sections 15, 35.7.
**Current repository state**
No primitives exist.
**Dependencies**
I01-S02-T01.
**Implementation steps**
1. Implement `LongAdder` counters and atomic min/max with overflow-safe duration/byte handling.
2. Implement bounded tables for aggregate-to-other/drop-new and cold-path least-recent eviction.
3. Publish active/max/dropped/overflow/policy and race-safe total/interval snapshots.
**Expected code and file changes**
- `com.madlava.core.aggregation.*` and concurrency tests.
**Behavioral contract**
- Callback updates never block globally or grow beyond limits; snapshots may be weakly consistent and declare this; resets never lose cumulative totals.
**Task-level tests**
- `I01-S02-T02-UT01` counter/min/max/delta boundary test, `mvn -B -Dtest=PrimitiveAggregatorTest test`, new.
- `I01-S02-T02-UT02` 64-thread cardinality race, `...BoundedTableConcurrencyTest`, new; size never exceeds cap and accounting balances.
**Acceptance criteria**
- [ ] All policies observable. [ ] Zero/overflow/negative-invalid inputs handled. [ ] No full-map copy on callback.
**Completion evidence**
- Race repetition logs and bounded-size assertions.
**Risks and notes**
- Prefer deterministic “other” accounting over hot-path LRU.

#### I01-S02-T03 — Implement scheduler and asynchronous writer
**Objective**
Create fixed-delay snapshot scheduling, bounded non-blocking writer, JSONL/atomic-latest modes, flush, rotation, retention and final snapshot.
**Specification references**
Sections 4.1, 16, 25.
**Current repository state**
Schema and lifecycle exist; no output path.
**Dependencies**
I01-S02-T01, I01-S02-T02.
**Implementation steps**
1. Add daemon scheduler with in-progress guard, sequence and feature/global deadlines.
2. Add daemon writer and bounded deque with configured overflow; serialize only off application callbacks.
3. Implement UTF-8 history/latest atomic move, rotation/age/size retention, safe paths, flush and bounded shutdown.
**Expected code and file changes**
- `com.madlava.reporting.*`, writer fixtures and integration tests.
**Behavioral contract**
- Submission is non-blocking; overload drops/throttles visibly; writer failure degrades reporting without app failure; partial/final metadata is accurate; files/bytes are bounded.
**Task-level tests**
- `I01-S02-T03-UT01` scheduler overlap/timeout/sequence, `mvn -B -Dtest=SnapshotSchedulerTest test`, new.
- `I01-S02-T03-UT02` modes/rotation/retention/atomicity, `...AsyncWriterTest`, new.
- `I01-S02-T03-UT03` saturated queue, denied directory, shutdown race, `...WriterFailureTest`, new; app completes and drops are reported.
**Acceptance criteria**
- [ ] Instrumented/application threads perform no file I/O. [ ] Every emitted line validates. [ ] shutdown respects configured timeout.
**Completion evidence**
- Thread-tagged I/O assertion, file inventory, schema reports.
**Risks and notes**
- Atomic move fallback must declare reduced atomicity.

#### I01-S02-T04 — Implement low-cost generic JVM features
**Objective**
Implement heap, non-heap, buffers, GC, thread statistics/CPU, process resources, class-loader insights, execution engine and self-observability.
**Specification references**
Sections 18.1–18.6, 18.20–18.23, 35.
**Current repository state**
Framework exists; no collectors.
**Dependencies**
I01-S02-T02, I01-S02-T03.
**Implementation steps**
1. Add MXBean/OS adapters with capability detection and negative/unavailable normalization.
2. Implement exact/interval/peak/trend fields and explicit Java-21 virtual-thread coverage limitation.
3. Expose feature state, source, accuracy, duration, errors, drops and agent CPU/heap/queue/transform diagnostics.
**Expected code and file changes**
- `com.madlava.collectors.jvm.*`, platform adapters, feature tests.
**Behavioral contract**
- Missing MXBeans yield `UNAVAILABLE`, never magic values; concurrent snapshots are safe; weak class-loader tracking does not retain loaders; no instrumentation is registered.
**Task-level tests**
- `I01-S02-T04-UT01` MXBean normal/unavailable/delta/peak tests, `mvn -B -Dtest=*JvmFeatureTest test`, new.
- `I01-S02-T04-UT02` concurrent snapshots and weak-loader GC, `...JvmFeatureConcurrencyTest`, new.
**Acceptance criteria**
- [ ] Every specified field/source/accuracy represented. [ ] Polling defaults match section 24.8. [ ] virtual-thread limitation explicit.
**Completion evidence**
- Unit XML, golden snapshot, weak-reference cleanup report.
**Risks and notes**
- OS/JFR-internal APIs require reflective, optional adapters.

### I01-S03 — Packaged MVP, viewer, documentation, and CI
**Stage objective:** certify a distributable baseline. **User-visible or architectural outcome:** packaged example and offline readable report. **Included tasks:** I01-S03-T01..T02. **Prerequisites:** I01-S02.
**Stage-level integration scenarios:** run actual JAR with valid/invalid/disabled configs, open multi-line fixture locally, attempt network and malicious content.
**Stage test suite:** I01-S03-ST01 `scripts/verify-packaged-agent.sh`; I01-S03-ST02 `scripts/verify-viewer.sh`. **Stage acceptance gate:** packaged demo works outside source resources and viewer remains offline. **Artifacts or documentation produced:** dev.1 distribution/docs/evidence. **Risks:** direct-class tests masking packaging defects.

#### I01-S03-T01 — Add packaged child-JVM and artifact certification
**Objective**
Test startup, shutdown, output, failure isolation and documentation commands using the produced JAR.
**Specification references**
Sections 6, 9, 26.5, 31, 41.
**Current repository state**
Runnable classes exist but packaged equivalence is unproved.
**Dependencies**
I01-S02-T04.
**Implementation steps**
1. Build deterministic example app and child-JVM harness that always selects `target` JAR.
2. Cover valid, disabled, invalid, precedence, interval, rotation, overflow, writer failure and shutdown-during-snapshot cases.
3. Inspect manifest/resources/dependencies/class versions and validate every output line.
**Expected code and file changes**
- `example.app`, `PackagedAgentIT`, scripts and fixtures.
**Behavioral contract**
- Target app exit/result remains unchanged; final snapshot appears when possible; failure is stderr/evidence only; distribution is self-contained.
**Task-level tests**
- `I01-S03-T01-UT01` `mvn -B -DskipUTs -Dit.test=PackagedAgentIT verify`, new; all scenarios exit as expected.
- `I01-S03-T01-UT02` `scripts/inspect-agent-jar.sh`, extended; archive contract passes.
**Acceptance criteria**
- [ ] Actual packaged JAR used. [ ] No test stub replaces runtime component. [ ] Docs command copied verbatim passes.
**Completion evidence**
- Child logs, outputs, archive listing and hashes.
**Risks and notes**
- Test asserts agent path differs from test classpath.

#### I01-S03-T02 — Deliver minimal offline viewer and baseline docs
**Objective**
Create static local-file parser/overview/raw/error UI and complete dev.1 usage documentation.
**Specification references**
Sections 3.2, 4.1, 6.3, 37.1–37.4, 37.8–37.12, 39.
**Current repository state**
No viewer/docs beyond spec.
**Dependencies**
I01-S01-T03, I01-S03-T01.
**Implementation steps**
1. Implement CSP-restricted HTML/CSS/JS with File API streaming, line errors, schema/version checks, overview and escaped raw JSON.
2. Add keyboard/accessibility basics, warnings for partial/drop/degrade and zero-network build/test.
3. Write README/getting-started/config/reference/privacy/limitations/testing/viewer docs and package ZIP.
**Expected code and file changes**
- `report-viewer/*`, browser tests, `docs/*`, README, sample report.
**Behavioral contract**
- Opens one/multiple files locally; malformed lines are isolated; no input reaches `innerHTML` unsafely; no server/network; large-file memory bounded by streaming/downsampling.
**Task-level tests**
- `I01-S03-T02-UT01` parser fixture tests, `npm test --prefix report-viewer`, new.
- `I01-S03-T02-UT02` `scripts/verify-viewer.sh`, new; CSP/network interception/XSS/accessibility smoke pass.
- `I01-S03-T02-UT03` `scripts/verify-doc-examples.sh`, new; commands and JSON examples validate.
**Acceptance criteria**
- [ ] Viewer opens packaged sample offline. [ ] Malicious fixture renders as text. [ ] All limitations visible.
**Completion evidence**
- Browser reports/screenshots, network log, doc validation and ZIP listing.
**Risks and notes**
- Vendored libraries must include licences and hashes.

## I02 — 0.1.0-alpha.1: Selective deep generic profiling

**MVP capability added:** one fail-safe ASM transformer, bootstrap bridge, method/constructor/throw/I/O/serialization/pool profiling. **End-user value:** narrow reproducible investigations with honest semantics. **Included stages:** I02-S01–S02. **Supported scenarios:** Java 11/17/21 generic packaged agent. **Distribution contents:** alpha.1 agent/viewer/config/docs. **Build and packaging commands:** `mvn -B -Pgeneric clean verify package`. **Launch and demonstration procedure:** run packaged example with `configs/method-and-io.json`; viewer displays ranked events. **Expected output:** bounded exact/sampled metrics with source/accuracy. **Documentation delivered:** filters, semantics, overhead/privacy cautions. **Compatibility matrix:** generic 11/17/21 mandatory. **Known limitations:** advanced sampling/dumps/control and Spark absent. **Cumulative regression inventory:** all I01 plus bytecode corpus, bridge/module/loader, feature semantics and three-JDK child JVM. **Iteration-level certification scenarios:** I02-IT01 `scripts/test-matrix.sh --generic`; I02-IT02 `mvn -B -Pgeneric -DbytecodeVerification verify`; I02-IT03 packaged privacy/overhead smoke. **Iteration release gate:** all carried suites plus three JDKs green. **Artifact acceptance checklist:** relocated ASM, bridge internal-only, one transformer, no app-class retention.

### I02-S01 — Instrumentation engine and runtime bridge
**Stage objective:** safe reusable bytecode infrastructure. **User-visible or architectural outcome:** selected methods can emit callbacks without semantic changes. **Included tasks:** I02-S01-T01..T02. **Prerequisites:** I01. **Stage-level integration scenarios:** transform corpus across returns/errors/constructors/loaders/modules; inject failures. **Stage test suite:** I02-S01-ST01 `mvn -B -Dtest=BytecodeCorpusIT verify`; I02-S01-ST02 `...ClassLoaderModuleIT`. **Stage acceptance gate:** ASM verifier and original-behavior oracle pass; failures use original bytes. **Artifacts or documentation produced:** relocated engine/embedded bridge. **Risks:** verifier errors and recursive loading.

#### I02-S01-T01 — Build composite transformer and instrumentation plan
**Objective**
Implement the sole primary transformer, compiled wildcard/signature filters, fast rejection, feature paths, optional retransform and fail-open errors.
**Specification references**
Sections 13, 24.4, 36.7–36.9.
**Current repository state**
No bytecode path; polling agent stable.
**Dependencies**
I01-S02-T02.
**Implementation steps**
1. Shade/relocate public ASM and compile configuration into immutable matchers/callback IDs.
2. Implement rejection order, exact signatures, one transformer registration and guarded optional retransform.
3. On transform error return null, count/rate-limit and disable only failing path after threshold.
**Expected code and file changes**
- `com.madlava.instrumentation.*`, shade config, filter/transform tests.
**Behavioral contract**
- Excludes win; internal/ASM/JVM infrastructure cannot be overridden; no application class is loaded for matching/hierarchy; retransformation defaults off/rate-limited.
**Task-level tests**
- `I02-S01-T01-UT01` filter/wildcard/overload/internal exclusions, `mvn -B -Dtest=InstrumentationPlanTest test`, new.
- `I02-S01-T01-UT02` transform-failure and one-transformer test, `...CompositeTransformerTest`, new; original class executes.
**Acceptance criteria**
- [ ] At most one primary transformer. [ ] ASM absent from public namespace/API. [ ] no enabled bytecode feature means no transformer.
**Completion evidence**
- Agent registration assertions, archive scan, fault-injection logs.
**Risks and notes**
- Hierarchy resolution uses class resources, never `Class.forName`.

#### I02-S01-T02 — Preserve bytecode semantics and bridge loader boundaries
**Objective**
Implement primitive callback bridge, reentrancy guards and advice for normal/exceptional/constructor paths with verified frames.
**Specification references**
Sections 14, 18.8, 18.11–18.12, 36.
**Current repository state**
Transformer shell exists; callbacks/advice absent.
**Dependencies**
I02-S01-T01.
**Implementation steps**
1. Build Java-11 embedded bridge with static primitive/string contracts, neutral returns and catch-all boundaries; append only when needed.
2. Implement separate finally-cleared guards and weak/ClassValue loader caches/module-safe resource hierarchy resolution.
3. Instrument every normal return, catch escaping Throwable without replacing it, `ATHROW`, and successful outermost constructors after initialization; verify frames.
**Expected code and file changes**
- bridge sources/resource, advice visitors, runtime dispatch, bytecode corpus.
**Behavioral contract**
- Return bits and Throwable identity preserved; synchronized/try-finally/recursive/lambda/bridge flags work; failed or chained constructors are not double counted; loader can collect; callback failure is neutral.
**Task-level tests**
- `I02-S01-T02-UT01` exhaustive method/ATHROW behavior oracle, `mvn -B -Dtest=MethodAdviceVerificationTest test`, new.
- `I02-S01-T02-UT02` constructor chain/inheritance/failed/uninitializedThis, `...ConstructorAdviceVerificationTest`, new.
- `I02-S01-T02-UT03` bootstrap/custom loader/module/recursive load/GC, `...BridgeClassLoaderIT`, new.
**Acceptance criteria**
- [ ] ASM CheckClassAdapter/JVM verification green. [ ] one exit gives one observation. [ ] bridge contains no forbidden code/deps.
**Completion evidence**
- Verification reports, original-vs-instrumented oracle, weak-loader GC logs.
**Risks and notes**
- Exceptional completion includes implicit propagation separately from explicit `ATHROW`.

### I02-S02 — Instrumented generic feature slice
**Stage objective:** deliver bounded opt-in profiling. **User-visible or architectural outcome:** methods, instances, throws, I/O, serialization and pools appear in reports/viewer. **Included tasks:** I02-S02-T01..T03. **Prerequisites:** I02-S01. **Stage-level integration scenarios:** nested calls, errors, EOF, rethrows/wrappers, Kryo nesting, rejected tasks under cardinality pressure. **Stage test suite:** I02-S02-ST01 `mvn -B -Dtest=InstrumentedFeaturesIT verify`; I02-S02-ST02 `scripts/test-matrix.sh --generic`. **Stage acceptance gate:** semantic counts balance, privacy and hot-path I/O assertions pass. **Artifacts or documentation produced:** alpha.1 config/examples/docs. **Risks:** double counting.

#### I02-S02-T01 — Implement method, instance, and Throwable profiling
**Objective**
Expose exact selective durations/constructions and distinct creation/explicit throw/JFR/propagation semantics.
**Specification references**
Sections 18.8, 18.11, 18.12, 24.15, 35.2–35.5.
**Current repository state**
Generic advice exists; feature aggregators absent.
**Dependencies**
I02-S01-T02.
**Implementation steps**
1. Aggregate started/completed/normal/exceptional duration and bounded histograms after pre-timer sampling.
2. Count only successful outermost constructions and independent Throwable creation, rethrow, wrapper and propagation streams.
3. Add optional JFR adapter/capability state, sampled depth-limited redacted sites and accuracy per source.
**Expected code and file changes**
- profiling features/runtime callbacks/config/schema/viewer rankings/tests.
**Behavioral contract**
- No arguments/returns/messages by default; same Throwable rethrow increments throw only; constructor failure does not create; coverage is never claimed complete for selected instrumentation.
**Task-level tests**
- `I02-S02-T01-UT01` method signatures/returns/errors/recursion/histogram bounds, `mvn -B -Dtest=MethodProfilingIT verify`, new.
- `I02-S02-T01-UT02` construction chains/failures/records/inner classes, `...InstanceCountingIT`, new.
- `I02-S02-T01-UT03` created-not-thrown/rethrow/wrap/implicit/JFR unavailable/privacy, `...ThrowablesIT`, new.
**Acceptance criteria**
- [ ] Counts and accuracy match event source. [ ] disabled by default. [ ] storm respects maxEntries/rate/throttle.
**Completion evidence**
- Golden reports and event-balance table.
**Risks and notes**
- JFR event duplicates require source-separated counts, not silent merging.

#### I02-S02-T02 — Implement stream/network I/O and serialization
**Objective**
Measure completed bytes/duration/errors without payload capture or delegated/nested double counting.
**Specification references**
Sections 18.13–18.15, 24.16, 35.4, 35.6.
**Current repository state**
Bridge exists; no feature paths.
**Dependencies**
I02-S01-T02.
**Implementation steps**
1. Instrument stream/file/channel/transfer/network operations and count actual returned bytes, EOF zero and close/error outcomes.
2. Instrument Java root serialization and reflectively detected Kryo compatible signatures using position deltas.
3. Add separate nesting guards, writer exclusions, anonymized endpoint/call-site groups and explicit byte accuracy.
**Expected code and file changes**
- I/O and serialization collectors/advice/config/tests/Kryo test fixtures.
**Behavioral contract**
- No payload/full endpoint/full path; attempted vs completed/error distinct; nested/delegated calls count once; Kryo absent leaves agent usable; hot path never writes files.
**Task-level tests**
- `I02-S02-T02-UT01` read/write/partial/EOF/error/delegation/transfer, `mvn -B -Dtest=StreamIoIT,NetworkIoIT verify`, new.
- `I02-S02-T02-UT02` Java/Kryo success/error/nesting/position delta/Kryo absent, `...SerializationIT`, new.
- `I02-S02-T02-UT03` privacy and writer self-exclusion, `...IoPrivacyTest`, new.
**Acceptance criteria**
- [ ] Byte accuracy truthful. [ ] payload scan negative. [ ] serialization errors preserve original Throwable.
**Completion evidence**
- Expected-vs-observed byte tables and secret scan.
**Risks and notes**
- Layered I/O attribution must be documented, not presented as object size.

#### I02-S02-T03 — Implement thread-pool profiling and three-JDK certification
**Objective**
Track pools/tasks weakly and certify all generic features on JDK 11/17/21 packaged agents.
**Specification references**
Sections 5.1, 18.16, 27.1–27.5.
**Current repository state**
No pool feature; only JDK11 mandatory.
**Dependencies**
I02-S02-T01, I02-S02-T02.
**Implementation steps**
1. Add weak pool registry and optional task wrapper for submitted/completed/failed/rejected, execution/queue time and bounded task types.
2. Add toolchain/container generic matrix and preserve per-lane logs.
3. Run packaged example/privacy/schema/artifact suites on each JDK and document runtime-specific availability.
**Expected code and file changes**
- thread-pool feature, matrix script/CI, compatibility docs.
**Behavioral contract**
- Wrapping preserves identity/exception/cancellation semantics; pools collect after GC; unavailable CPU is explicit; invalid/unsupported Java returns without affecting app.
**Task-level tests**
- `I02-S02-T03-UT01` execution/failure/rejection/cancel/weak GC, `mvn -B -Dtest=ThreadPoolsIT verify`, new.
- `I02-S02-T03-UT02` `scripts/test-matrix.sh --generic`, extended; 11/17/21 all green, Java<11 policy tested where fixture available.
**Acceptance criteria**
- [ ] Three mandatory generic lanes. [ ] class major remains 55. [ ] no pool retention.
**Completion evidence**
- Matrix summary and per-JDK packaged reports.
**Risks and notes**
- Virtual-thread pool semantics are reported separately where observable.

## I03 — 0.1.0-alpha.2: Advanced diagnostics and bounded operations

**MVP capability added:** sampling/allocation/contention, structured dumps/off-heap, MBean control, reload, incidents and adaptive throttling. **End-user value:** capture bounded evidence around incidents without restart. **Included stages:** I03-S01–S02. **Supported scenarios:** generic JDK 11/17/21, capability-aware fallbacks. **Distribution contents:** alpha.2 agent/viewer and incident examples. **Build and packaging commands:** `mvn -B -Pgeneric clean verify package`. **Launch and demonstration procedure:** launch packaged stress app, invoke local MBean dump/temporary sampling, open incident. **Expected output:** referenced bounded artifacts and visible throttle decisions. **Documentation delivered:** control permissions, dump privacy, overhead envelope. **Compatibility matrix:** generic lanes plus JFR/fallback variants. **Known limitations:** no Spark. **Cumulative regression inventory:** I01/I02 plus dumps, sampling, reload, incident, stress, long-run and benchmarks. **Iteration-level certification scenarios:** I03-IT01 generic matrix; I03-IT02 30-minute bounded stress; I03-IT03 JMH and application overhead comparison; I03-IT04 packaged MBean demo. **Iteration release gate:** no budget/resource/privacy breach; regression green. **Artifact acceptance checklist:** dump retention/permissions, no unauthenticated listener, benchmark evidence.

### I03-S01 — Sampling and dump features
**Stage objective:** capability-aware deep diagnostics. **User-visible or architectural outcome:** bounded stacks, allocation, contention and dump artifacts. **Included tasks:** I03-S01-T01..T02. **Prerequisites:** I02. **Stage-level integration scenarios:** JFR available/unavailable, virtual/platform threads, deadlock, direct/mapped buffers, dump cooldown/retention. **Stage test suite:** I03-S01-ST01 `mvn -B -Dtest=SamplingAndContentionIT verify`; I03-S01-ST02 `...DumpArtifactsIT`. **Stage acceptance gate:** truthful availability and artifact bounds. **Artifacts or documentation produced:** dump schemas/examples. **Risks:** JFR variability and heavy capture.

#### I03-S01-T01 — Implement execution, allocation, and contention sampling
**Objective**
Provide JFR-preferred and MXBean fallback sampling with bounded stacks/groups and explicit coverage.
**Specification references**
Sections 18.9, 18.10, 18.17, 35.5, 35.9.
**Current repository state**
No sampling features.
**Dependencies**
I02-S02-T03.
**Implementation steps**
1. Add Java-11 MXBean sampler and reflective Java-17/21 RecordingStream adapters.
2. Aggregate CPU/wall stacks, allocation events/thread bytes, monitors/parks/waits with bounded depth/cardinality.
3. Report source, coverage, virtual-thread limitation, sampling/throttling and capability fallback.
**Expected code and file changes**
- sampling/JFR adapters, collectors, config/schema/viewer and tests.
**Behavioral contract**
- Sampling threads are daemon/bounded; carrier CPU never claimed as virtual CPU; constructor counts never substitute allocation; enabling contention is explicit and restored safely.
**Task-level tests**
- `I03-S01-T01-UT01` deterministic fake-event aggregation/fallback, `mvn -B -Dtest=SamplingFeatureTest test`, new.
- `I03-S01-T01-UT02` packaged JFR/MXBean/unsupported matrix, `...SamplingFeatureIT`, new.
- `I03-S01-T01-UT03` cardinality/storm/stop test, `...SamplingStressTest`, new.
**Acceptance criteria**
- [ ] Disabled defaults. [ ] no unbounded stack/event retention. [ ] accuracy and missing-data reason always present.
**Completion evidence**
- Per-JDK source/coverage reports and stress maxima.
**Risks and notes**
- Do not link agent to non-baseline JFR APIs.

#### I03-S01-T02 — Implement thread, heap, and off-heap dumps
**Objective**
Produce separate bounded referenced artifacts for filtered threads/deadlocks, class histograms/tracked objects and off-heap evidence.
**Specification references**
Sections 18.7, 18.18, 18.19, 20, 22.
**Current repository state**
No dump subsystem.
**Dependencies**
I03-S01-T01, I01-S02-T03.
**Implementation steps**
1. Add thread filters/triggers/state display/locks/deadlocks and JSON/text artifact rotation.
2. Add class histogram, bounded weak tracked objects and guarded HPROF; label shallow/activation limitations.
3. Add direct/mapped/optional Unsafe/foreign/NMT sources with owner-vs-subsystem accuracy and safe owner-only paths.
**Expected code and file changes**
- dump features/artifact writer/config/schema/tests.
**Behavioral contract**
- Cooldown/count/size/retention enforced; original thread name drives filters; no retained-size or ownership fabrication; force-GC/HPROF explicit; traversal rejected.
**Task-level tests**
- `I03-S01-T02-UT01` filters/state/deadlock/triggers, `mvn -B -Dtest=ThreadDumpIT verify`, new.
- `I03-S01-T02-UT02` histogram/tracked/pre-activation/failed dump, `...HeapDumpIT`, new.
- `I03-S01-T02-UT03` direct/mapped/NMT unavailable/privacy/permissions/retention, `...OffHeapDumpIT`, new.
**Acceptance criteria**
- [ ] Main snapshot references valid artifacts. [ ] artifacts remain within every bound. [ ] permissions best effort and reported.
**Completion evidence**
- Artifact inventories, schemas, permission/retention logs.
**Risks and notes**
- HPROF is high impact and never automatic under default profile.

### I03-S02 — Runtime operations and overhead governance
**Stage objective:** bounded on-demand operation and self-protection. **User-visible or architectural outcome:** local control, incidents and automatic degradation. **Included tasks:** I03-S02-T01..T02. **Prerequisites:** I03-S01. **Stage-level integration scenarios:** reload mutable/immutable fields, trigger bundle, exceed each budget, recover, concurrent stop. **Stage test suite:** I03-S02-ST01 `mvn -B -Dtest=RuntimeControlIT,IncidentRecorderIT verify`; I03-S02-ST02 `...OverheadControllerStressIT`. **Stage acceptance gate:** no remote listener, bounds and decisions visible. **Artifacts or documentation produced:** control reference/benchmark baseline. **Risks:** control races.

#### I03-S02-T01 — Implement local runtime control, reload, and incidents
**Objective**
Expose permission-bound platform MBean commands, safe configuration reload and bounded incident history/bundles.
**Specification references**
Sections 22, 23, 24.17.
**Current repository state**
Features are startup-configured only.
**Dependencies**
I03-S01-T02.
**Implementation steps**
1. Register local platform MBean for dump/histogram/JFR/sampling/interval/polling controls; open no connector.
2. Validate reload into immutable snapshot; apply supported changes atomically per feature and reject instrumentation paths not installed unless explicit retransform allowed.
3. Maintain bounded circular history and assemble cooldown/count/size-limited incident directories asynchronously.
**Expected code and file changes**
- control/reload/incident packages, MBean interface, tests/docs.
**Behavioral contract**
- Local JVM permissions required; invalid reload preserves last good config; partial reconfigure isolates feature; bundles redact config and cannot escape directory; commands are idempotent/race-safe.
**Task-level tests**
- `I03-S02-T01-UT01` MBean permission/commands/no-socket test, `mvn -B -Dtest=RuntimeControlIT verify`, new.
- `I03-S02-T01-UT02` atomic reload/invalid/immutable/concurrent test, `...ConfigurationReloadIT`, new.
- `I03-S02-T01-UT03` trigger/cooldown/retention/redaction/failure bundle, `...IncidentRecorderIT`, new.
**Acceptance criteria**
- [ ] No unauthenticated TCP listener. [ ] invalid control never destabilizes app. [ ] all incident limits enforced.
**Completion evidence**
- MBean transcript, socket scan, bundle listing and redaction scan.
**Risks and notes**
- Runtime enablement is limited to preinstalled safe paths.

#### I03-S02-T02 — Implement overhead controller and certify resource bounds
**Objective**
Measure agent CPU/heap/snapshot/queue/event/output budgets and apply documented ordered throttling/circuit breaking.
**Specification references**
Sections 4.1, 15, 21, 29.
**Current repository state**
Self metrics exist without adaptive policy.
**Dependencies**
I03-S02-T01.
**Implementation steps**
1. Evaluate configured budgets on dedicated cadence with hysteresis and deterministic decisions.
2. Apply ordered stack/allocation/detail/plan/interval/subfeature/breaker actions and reversible recovery where safe.
3. Add JMH callbacks/aggregation/writer benchmarks, baseline-vs-agent workloads, 30-minute cardinality/writer stress and evidence format.
**Expected code and file changes**
- overhead controller, JMH benchmarks, stress scripts, docs/evidence schema.
**Behavioral contract**
- Decisions never block callbacks; each has evidence/threshold/time/affected feature; memory/queues/output remain capped; benchmark variance is reported honestly.
**Task-level tests**
- `I03-S02-T02-UT01` deterministic budget/order/hysteresis/recovery, `mvn -B -Dtest=OverheadControllerTest test`, new.
- `I03-S02-T02-UT02` `mvn -B -Pbenchmarks verify`, new; produces comparison without claiming pass outside configured thresholds.
- `I03-S02-T02-UT03` `scripts/stress-generic.sh --minutes 30`, new; caps hold, app stays responsive.
**Acceptance criteria**
- [ ] Default target ≤1% agent CPU subject to documented workload envelope. [ ] all throttle decisions visible. [ ] no unbounded retained state.
**Completion evidence**
- JMH JSON, workload comparison, max-resource time series.
**Risks and notes**
- Release blocks on configured threshold breach, sustained growth, lost app semantics, or missing measurement.

## I04 — 0.1.0-beta.1: Spark 3.5 driver diagnostics

**MVP capability added:** Spark-neutral adapter SPI plus complete Spark 3.5.9/Scala 2.12 Classic-driver inventory, lifecycle, plans, results, scheduler, storage and insights. **End-user value:** correlate JVM pressure with driver-side Spark evidence. **Included stages:** I04-S01–S02. **Supported scenarios:** local/standalone/driver startup for Java/Scala Spark 3.5.9 on JDK11/17. **Distribution contents:** same dependency-free beta.1 agent and viewer. **Build and packaging commands:** `mvn -B -Pspark35 -Dspark35.version=3.5.9 clean verify package`. **Launch and demonstration procedure:** `spark-submit --driver-java-options '-javaagent:...beta.1.jar=config=...'` packaged fixture. **Expected output:** correlated, bounded, content-free Spark sections. **Documentation delivered:** Spark deployment/support/interpretation. **Compatibility matrix:** generic 11/17/21 plus spark35-jdk11/17. **Known limitations:** Spark4/PySpark certification deferred. **Cumulative regression inventory:** all generic plus Spark core scenarios, CSV and serialization failures on both lanes. **Iteration-level certification scenarios:** I04-IT01 `scripts/test-matrix.sh --spark35`; I04-IT02 standalone smoke; I04-IT03 packaged CSV/SER suites; I04-IT04 artifact no-Spark-dependency scan. **Iteration release gate:** both exact lanes and generic regression pass. **Artifact acceptance checklist:** no Spark classes/deps in JAR; unsupported 3.5/JDK21 disables Spark only.

### I04-S01 — Spark adapter, compatibility, and model lifecycle
**Stage objective:** loader-safe Spark 3.5 observation foundation. **User-visible or architectural outcome:** runtime/session/DataFrame/plan inventory. **Included tasks:** I04-S01-T01..T02. **Prerequisites:** I03. **Stage-level integration scenarios:** Spark absent, supported, unsupported JDK, custom loader, sessions/DF GC, AQE and never-force planning. **Stage test suite:** I04-S01-ST01 `mvn -B -Pspark35 -Dtest=Spark35LifecycleIT verify`; I04-S01-ST02 `...SparkCompatibilityIT`. **Stage acceptance gate:** generic remains available in every failure/unsupported case. **Artifacts or documentation produced:** adapter signature catalog. **Risks:** Spark internal drift/loader retention.

#### I04-S01-T01 — Implement Spark-neutral adapter SPI and compatibility gate
**Objective**
Detect Spark reflectively, select exact version/Scala/JDK adapter, isolate failures and disable only Spark on unsupported combinations.
**Specification references**
Sections 5.2–5.4, 19.1–19.3, 24.18 compatibility, 27.3.
**Current repository state**
Generic agent has no Spark linkage.
**Dependencies**
I02-S01-T02, I03-S02-T02.
**Implementation steps**
1. Define generic SPI/DTOs and signature catalog; load adapter with Spark defining loader without static Spark references.
2. Detect version, Scala binary, Classic/Connect and JDK; enforce supported/unsupported/experimental policy.
3. Add per-capability availability/failure isolation and weak loader caches.
**Expected code and file changes**
- `collectors.spark.spi/adapter35`, compatibility signatures/tests/profiles.
**Behavioral contract**
- Spark absent/disabled/unsupported does not load Spark types or affect generic features; 3.5+21 reports `UNSUPPORTED_COMBINATION`; Spark4+11 likewise; adapter failure is feature-scoped.
**Task-level tests**
- `I04-S01-T01-UT01` absent/fake/signature/version/policy test, `mvn -B -Dtest=SparkCompatibilityTest test`, new.
- `I04-S01-T01-UT02` custom-loader/weak-GC/failure test, `...SparkAdapterClassLoaderIT`, new.
**Acceptance criteria**
- [ ] No Spark dependency in agent. [ ] exact 3.5.9 signatures centrally pinned. [ ] unsupported policy truthful.
**Completion evidence**
- Dependency/archive scan and compatibility reports.
**Risks and notes**
- Signature mismatch degrades one capability, not the whole agent.

#### I04-S01-T02 — Implement runtime, sessions, DataFrames, and plans
**Objective**
Track Spark configuration/inventory and bounded weak lifecycle/structural plan evidence without forcing computation.
**Specification references**
Sections 19.4–19.9, 24.18 runtime through catalyst memory.
**Current repository state**
Adapter gate exists; models absent.
**Dependencies**
I04-S01-T01.
**Implementation steps**
1. Capture redacted runtime/app/master/deploy/version/Scala/config and session create/new/close/idle liveness.
2. Track DataFrame/Dataset weak identity, schema statistics and bounded parent relations.
3. Capture logical/analyzed/optimized/physical/AQE structural fingerprints/phases only when naturally materialized; optional redacted bounded text and sampled Catalyst churn.
**Expected code and file changes**
- Spark35 runtime/session/DF/plan collectors/advice/schema/viewer/tests.
**Behavioral contract**
- Never force planning/action; no rows/SQL literals/paths; weak refs and all limits enforced; equivalent plans use documented fingerprint; pre-activation coverage declared.
**Task-level tests**
- `I04-S01-T02-UT01` session/newSession/close/idle/GC, `mvn -B -Pspark35 -Dtest=SparkSessionLifecycleIT verify`, new.
- `I04-S01-T02-UT02` DF branches/joins/unions/schema/parents/limits/GC, `...SparkDataFrameInventoryIT`, new.
- `I04-S01-T02-UT03` phases/AQE/no-force/redaction/fingerprint/churn, `...SparkPlanTrackingIT`, new.
**Acceptance criteria**
- [ ] Content never captured. [ ] tracked sets bounded/collectable. [ ] plan availability/accuracy explicit.
**Completion evidence**
- Golden correlated reports, action counter proving no forced planning, privacy scan.
**Risks and notes**
- Typed Dataset default remains off.

### I04-S02 — Spark execution, resources, insights, and Spark35 certification
**Stage objective:** complete usable Spark35 vertical slice. **User-visible or architectural outcome:** results, broadcasts, jobs/stages/tasks/shuffle/spill/cache/pressure/insights. **Included tasks:** I04-S02-T01..T02. **Prerequisites:** I04-S01. **Stage-level integration scenarios:** core section-27.6 scenarios plus all CSV/Java/Kryo failures on both lanes. **Stage test suite:** I04-S02-ST01 `scripts/test-spark.sh 3.5.9 11`; I04-S02-ST02 same JDK17; I04-S02-ST03 standalone smoke. **Stage acceptance gate:** correlation, privacy and thresholds pass on both exact lanes. **Artifacts or documentation produced:** beta reports/viewer/docs. **Risks:** high event cardinality/skew.

#### I04-S02-T01 — Implement Spark execution and resource lifecycle metrics
**Objective**
Correlate driver results, broadcasts, jobs/stages/bounded tasks, shuffle/spill and persistence lifecycle.
**Specification references**
Sections 19.10–19.17, 24.18 corresponding groups.
**Current repository state**
Model inventory exists without execution evidence.
**Dependencies**
I04-S01-T02.
**Implementation steps**
1. Instrument collect/take/local iterator/result handling and user/SQL broadcasts with size/source/liveness/call-site bounds.
2. Register scheduler observations for job/stage/task outcomes/delays, bounded representative details, shuffle skew and spill.
3. Track persist/materialize/reuse/unpersist and generate evidence-based lifecycle warnings.
**Expected code and file changes**
- Spark execution/scheduler/storage collectors, correlations, tests.
**Behavioral contract**
- Driver-result size source/accuracy explicit; iterator is not assumed materialized; task details bounded while aggregates exact where available; warnings cite versioned thresholds/evidence; no data payload.
**Task-level tests**
- `I04-S02-T01-UT01` small/large collect/take/iterator/broadcast lifecycle, `mvn -B -Pspark35 -Dtest=SparkDriverResultsIT,SparkBroadcastIT verify`, new.
- `I04-S02-T01-UT02` jobs/stages/failures/tasks/skew/spill/AQE, `...SparkSchedulerIT`, new.
- `I04-S02-T01-UT03` persist/materialize/reuse/unpersist/GC/warnings, `...SparkStorageIT`, new.
**Acceptance criteria**
- [ ] Limits remain under stress. [ ] correlations stable. [ ] all warning formulas documented.
**Completion evidence**
- Scenario matrix/golden reports/max-cardinality log.
**Risks and notes**
- Listener callbacks must enqueue minimally and never block Spark threads.

#### I04-S02-T02 — Implement pressure/insights and certify Spark 3.5 failures
**Objective**
Compute versioned evidence scores/insights and validate CSV/serialization/application failures on mandatory Spark35 lanes.
**Specification references**
Sections 19.20–19.21, 27.6–27.9, 35.1.
**Current repository state**
Raw Spark evidence exists; insight/test matrix incomplete.
**Dependencies**
I04-S02-T01, I02-S02-T01, I02-S02-T02.
**Implementation steps**
1. Define versioned 0–100 component/overall score formula and NORMAL/ELEVATED/HIGH/CRITICAL thresholds.
2. Emit only evidence-backed large-result/broadcast/planning/shuffle/listener/plan/session/cache insights.
3. Add deterministic CSV-001..010, SER-JAVA-001..004, SER-KRYO-001..004 and additional failure fixtures/assertions to both lanes.
**Expected code and file changes**
- insight engine/docs, Spark fixtures/tests, matrix scripts.
**Behavioral contract**
- Insight wording says evidence/next step, never automatic root cause; malformed rows/messages/paths absent; creation/throw/propagation/wrapping and Spark correlations remain distinct.
**Task-level tests**
- `I04-S02-T02-UT01` formula boundary/evidence/missing-input tests, `mvn -B -Dtest=SparkPressureInsightsTest test`, new.
- `I04-S02-T02-UT02` `scripts/test-spark-failures.sh --spark 3.5.9 --jdk 11`, new.
- `I04-S02-T02-UT03` same JDK17, new; each fixture outcome and privacy assertion passes.
**Acceptance criteria**
- [ ] Exact version pins central. [ ] no malformed content/secret leak. [ ] both mandatory lanes pass with actual packaged JAR.
**Completion evidence**
- Failure matrix, report correlations, privacy scan and score golden files.
**Risks and notes**
- Expected Spark wrapper classes may vary; signature expectations are lane-specific.

## I05 — 0.1.0-rc.1: Spark 4 and PySpark certification

**MVP capability added:** Spark 4.2/Scala2.13 adapter, Py4J lifecycle/calls/proxies/connections, full PySpark matrix and optional driver internals. **End-user value:** same dependency-free agent across all supported Spark/JDK/Python lanes. **Included stages:** I05-S01–S02. **Supported scenarios:** all specification-supported Classic modes/APIs. **Distribution contents:** rc.1 artifacts/docs. **Build and packaging commands:** `mvn -B -Pspark4 -Dspark4.version=4.2.0 clean verify`; PySpark scripts. **Launch and demonstration procedure:** run packaged Java/Scala and PySpark examples with driver options. **Expected output:** version-adapted correlations and Py4J evidence. **Documentation delivered:** full matrix/unsupported combinations/PySpark. **Compatibility matrix:** all four Spark lanes and both PySpark environments. **Known limitations:** Connect excluded; viewer final UX deferred. **Cumulative regression inventory:** all generic/Spark35 plus Spark42/PySpark failures/signatures. **Iteration-level certification scenarios:** I05-IT01 full matrix; I05-IT02 isolated PySpark matrix; I05-IT03 standalone; I05-IT04 unsupported-policy tests. **Iteration release gate:** every mandatory lane green; invalid lanes disable Spark only. **Artifact acceptance checklist:** one identical agent contract, no Spark/Python deps.

### I05-S01 — Spark 4 adapter and Py4J diagnostics
**Stage objective:** extend adapter without regressing 3.5. **User-visible or architectural outcome:** Spark4 and Py4J observability. **Included tasks:** I05-S01-T01..T02. **Prerequisites:** I04. **Stage-level integration scenarios:** same model/execution suite on 4.2 plus Py4J binding GC/calls/proxies/connections. **Stage test suite:** I05-S01-ST01 `mvn -B -Pspark4 -Dtest=Spark42*IT verify`; I05-S01-ST02 `...Py4JDiagnosticsIT`. **Stage acceptance gate:** adapter-specific code isolated and both Spark lines green. **Artifacts or documentation produced:** Spark42 signature catalog. **Risks:** binary differences and binding identity.

#### I05-S01-T01 — Implement and certify Spark 4.2 adapter
**Objective**
Adapt all I04 Spark capabilities to Spark 4.2.0/Scala2.13 on JDK17/21 and enforce Spark4+JDK11 unsupported policy.
**Specification references**
Sections 5.2, 19, 27.3, 27.6.
**Current repository state**
Spark35 adapter/capabilities stable.
**Dependencies**
I04-S02-T02.
**Implementation steps**
1. Add 4.2 signature catalog and adapter implementations behind unchanged SPI/DTOs.
2. Run identical core/AQE/result/broadcast/scheduler/shuffle/spill/storage/failure scenarios on both valid JDKs.
3. Assert generic operation and Spark-only `UNSUPPORTED_COMBINATION` on JDK11.
**Expected code and file changes**
- `adapter42`, profile/signatures/tests/docs.
**Behavioral contract**
- No cross-loading Scala lines; capability mismatch degrades locally; schema semantics remain compatible with beta reports; generic features unaffected.
**Task-level tests**
- `I05-S01-T01-UT01` signature/SPI contract, `mvn -B -Pspark4 -Dtest=Spark42CompatibilityTest test`, new.
- `I05-S01-T01-UT02` JDK17 packaged suite, `scripts/test-spark.sh 4.2.0 17`, new.
- `I05-S01-T01-UT03` JDK21 suite and JDK11 negative policy, corresponding matrix commands, new.
**Acceptance criteria**
- [ ] Scala2.13 only. [ ] same schema/accuracy contracts. [ ] 3.5 regression remains green.
**Completion evidence**
- Three policy/lane reports and signature diff.
**Risks and notes**
- Internal signatures are centralized for patch upgrades.

#### I05-S01-T02 — Implement Py4J and optional driver-internal diagnostics
**Objective**
Track bounded Py4J bindings/proxies/connections/calls and optional listener/scheduler/result/RPC internals without values.
**Specification references**
Sections 19.18–19.19, 24.18 Py4J/driver internals.
**Current repository state**
No Py4J feature paths.
**Dependencies**
I05-S01-T01.
**Implementation steps**
1. Track Java-to-Python bindings and Python-to-Java proxies weakly, reconcile periodically and record age/type/creation method.
2. Aggregate connections/calls/callback outcomes/latency/CPU with nesting guard and bounded methods; return type only by default.
3. Add optional version-specific listener bus/scheduler queue/task-result/RPC duration observations and insight thresholds.
**Expected code and file changes**
- Py4J/internal collectors/advice/config/schema/viewer/tests.
**Behavioral contract**
- Never capture args/object contents/auth tokens/payloads; binding disappearance is reconciled; callback errors preserved; network byte layer off; internal failure degrades subcapability.
**Task-level tests**
- `I05-S01-T02-UT01` binding/proxy create/delete/GC/reconcile/limits, `mvn -B -Dtest=Py4JBindingTest test`, new.
- `I05-S01-T02-UT02` nested/slow/error/post-stop calls and privacy, `...Py4JCallsIT`, new.
- `I05-S01-T02-UT03` driver-internal available/missing/failure/queue bounds, `...SparkDriverInternalsIT`, new.
**Acceptance criteria**
- [ ] All section-24 limits enforced. [ ] secret/value scan negative. [ ] feature off when Py4J absent.
**Completion evidence**
- Lifecycle reconciliation reports and privacy scans.
**Risks and notes**
- Object IDs must be anonymized/stable only within process.

### I05-S02 — Full Spark/PySpark compatibility certification
**Stage objective:** certify all mandatory JVM and Python lanes. **User-visible or architectural outcome:** release-candidate support contract. **Included tasks:** I05-S02-T01. **Prerequisites:** I05-S01. **Stage-level integration scenarios:** section 27.10 functional and PY-EX-001..006 on isolated environments; all prior failure suites on Spark42. **Stage test suite:** I05-S02-ST01 `scripts/test-full-matrix.sh`; I05-S02-ST02 `scripts/test-pyspark.sh --all`. **Stage acceptance gate:** all valid lanes pass and unsupported lanes are policy successes only. **Artifacts or documentation produced:** matrix summary/log archive. **Risks:** environment leakage.

#### I05-S02-T01 — Build isolated full Java/Spark/PySpark matrix
**Objective**
Automate exact pinned certification, Python isolation, full failure attribution and cumulative lane reporting.
**Specification references**
Sections 27–28.
**Current repository state**
Generic/Spark JVM lanes exist; full PySpark and consolidated matrix absent.
**Dependencies**
I05-S01-T02.
**Implementation steps**
1. Implement host/container matrix with isolated Maven caches and `.venv-spark35`/`.venv-spark42`, central exact properties/env pins.
2. Execute mandatory Java/Scala core, CSV, serialization and additional failures on all four valid lanes.
3. Execute PySpark functional scenarios and PY-EX-001..006, assert Py4J binding/correlation/output/privacy, preserve logs and nonzero on any valid failure.
**Expected code and file changes**
- matrix/Python scripts, fixtures, tests, CI workflows and support docs.
**Behavioral contract**
- Invalid combinations are explicit policy checks, never silently run/failed/skipped; no mandatory test skip; packaged JAR always used; source/test version pins centralized.
**Task-level tests**
- `I05-S02-T01-UT01` `scripts/test-full-matrix.sh --jdk11 ... --jdk17 ... --jdk21 ...`, extended.
- `I05-S02-T01-UT02` `scripts/test-pyspark.sh --spark35 3.5.9 --spark4 4.2.0`, new.
- `I05-S02-T01-UT03` matrix self-tests inject lane failure/missing tool/invalid combo and assert summary/exit/log preservation, new.
**Acceptance criteria**
- [ ] Seven JVM lanes and two Python environments certified as applicable. [ ] all required scenarios explicit in summary. [ ] no payload/content leak.
**Completion evidence**
- Machine-readable matrix result, per-lane logs/reports/environment locks.
**Risks and notes**
- Missing mandatory runtime is a certification failure, not a skip.

## I06 — 0.1.0: Complete viewer, hardening, and release

**MVP capability added:** production-quality offline analysis, full docs and reproducible signed/checksummed release. **End-user value:** complete installable MadLava 0.1.0. **Included stages:** I06-S01–S02. **Supported scenarios:** full contract. **Distribution contents:** agent JAR, viewer ZIP, complete project ZIP, specification, SHA256SUMS, notes/changelog; source/Javadoc JAR, schema, benchmark, SBOM/provenance. **Build and packaging commands:** `scripts/release.sh 0.1.0` after `scripts/test-full-matrix.sh`. **Launch and demonstration procedure:** extract release outside source tree; run documented generic, Spark35, Spark42 and PySpark examples using release JAR; open each report in release viewer offline. **Expected output:** schema-v3 reports/artifacts and accessible dashboards. **Documentation delivered:** complete user/reference/architecture/design/testing/security/support/viewer/release set. **Compatibility matrix:** every prior lane mandatory. **Known limitations:** only documented section-32/non-goals; Connect excluded. **Cumulative regression inventory:** every task/stage/iteration suite I01–I06, compatibility, performance, security, viewer, reproducibility. **Iteration-level certification scenarios:** I06-IT01 full matrix; I06-IT02 viewer browsers/no-network/large/malicious/backward corpus; I06-IT03 8-hour stability; I06-IT04 performance/privacy/security; I06-IT05 two clean release builds byte-identical and artifact inspection; I06-IT06 execute every doc example from extracted archives. **Iteration release gate:** zero mandatory failures/skips/uncovered normative requirements; all artifacts/checksums valid. **Artifact acceptance checklist:** exact filenames, manifests, schema, viewer inside project archive, licences, SBOM/provenance, release metadata.

### I06-S01 — Complete offline report viewer and documentation
**Stage objective:** make every stable report usable and interpretable offline. **User-visible or architectural outcome:** timelines, rankings, tables, details, warnings, raw/export and accessible UX. **Included tasks:** I06-S01-T01..T02. **Prerequisites:** I05. **Stage-level integration scenarios:** single/multiple/rotated/history/latest/incident reports, v3 corpus, malformed/huge/malicious inputs, no network. **Stage test suite:** I06-S01-ST01 `scripts/verify-viewer.sh --all-browsers`; I06-S01-ST02 `scripts/verify-doc-examples.sh --release`. **Stage acceptance gate:** section-37 contract and WCAG-oriented automated/manual checklist pass. **Artifacts or documentation produced:** final viewer/docs. **Risks:** browser memory/XSS.

#### I06-S01-T01 — Complete viewer navigation, visualizations, tables, and export
**Objective**
Implement all section-37 viewer behavior over stable schema while remaining static, secure and bounded.
**Specification references**
Sections 37.1–37.12.
**Current repository state**
Minimal parser/overview exists.
**Dependencies**
I05-S02-T01.
**Implementation steps**
1. Add global navigation/time range/file comparison, overview health/feature states/insights/incidents.
2. Add bounded/downsampled timelines and rankings, sortable/filterable/paginated tables, collapsible detail and linked correlations.
3. Add raw/JSON/CSV export, warning/accuracy/availability explanations, keyboard/focus/contrast/responsive UX and safe parser workers.
**Expected code and file changes**
- viewer modules/styles/vendor assets/tests/sample corpus.
**Behavioral contract**
- File API only; no backend/network/eval/unsafe HTML; malformed records isolated; unsupported version clear; exports preserve redaction; huge inputs bounded and progress/cancel capable.
**Task-level tests**
- `I06-S01-T01-UT01` parser/model/backward/malformed/large tests, `npm test --prefix report-viewer`, extended.
- `I06-S01-T01-UT02` Playwright navigation/chart/table/export/accessibility suite, `scripts/verify-viewer.sh --all-browsers`, extended.
- `I06-S01-T01-UT03` CSP/XSS/no-network/zip inspection, same command, extended.
**Acceptance criteria**
- [ ] Every feature envelope navigable. [ ] all warnings visible. [ ] zero external requests and executable injection.
**Completion evidence**
- Browser matrix, accessibility report, memory profile, screenshots/network trace.
**Risks and notes**
- Prefer vendored minimal dependencies with third-party notices.

#### I06-S01-T02 — Complete documentation and interpretation contracts
**Objective**
Publish executable setup/config/deployment/investigation/security/semantics/support/release documentation.
**Specification references**
Sections 3–5, 20, 32, 34–35, 39, 41.
**Current repository state**
Incremental docs exist; final cross-feature reference incomplete.
**Dependencies**
I06-S01-T01.
**Implementation steps**
1. Generate configuration/schema/feature field references and hand-write lifecycle, architecture, privacy, accuracy, overhead and supported/unsupported guidance.
2. Document packaged generic/Spark/PySpark launch, viewer, controls/incidents, filters and comparative investigation workflow.
3. Validate every command/config/link/field against extracted release and ensure no post-0.1 claim.
**Expected code and file changes**
- README, docs tree, MkDocs config, changelog/security/contribution/licence/notices.
**Behavioral contract**
- Claims match certified lanes; evidence is not causality; limitations/missing data/accuracy are prominent; all text English and examples privacy-safe.
**Task-level tests**
- `I06-S01-T02-UT01` generated-reference drift/link/spell/config validation, `scripts/verify-docs.sh`, new.
- `I06-S01-T02-UT02` extracted-artifact generic/Spark/PySpark/viewer examples, `scripts/verify-doc-examples.sh --release`, extended.
**Acceptance criteria**
- [ ] Every public property/output field documented. [ ] every command executes. [ ] support claims equal matrix.
**Completion evidence**
- Link/schema/example reports and documentation inventory.
**Risks and notes**
- Generated reference inputs are configuration/schema metadata, not duplicated tables.

### I06-S02 — Hardening and final release engineering
**Stage objective:** certify safety, stability, reproducibility and release integrity. **User-visible or architectural outcome:** publishable 0.1.0 archive set. **Included tasks:** I06-S02-T01..T02. **Prerequisites:** I06-S01. **Stage-level integration scenarios:** long-running high-cardinality workload, writer/disk/JFR/Spark failures, malicious config/report, dual clean builds and extracted demos. **Stage test suite:** I06-S02-ST01 `scripts/certify-release.sh 0.1.0`; I06-S02-ST02 `scripts/verify-release-artifacts.sh dist/0.1.0`. **Stage acceptance gate:** all gates green with zero mandatory skip. **Artifacts or documentation produced:** final release/evidence. **Risks:** late platform variance/supply chain.

#### I06-S02-T01 — Execute final safety, privacy, performance, and stability hardening
**Objective**
Prove fail-safe behavior, bounded resources, privacy, class-loader/module safety and documented overhead across the full product.
**Specification references**
Sections 4, 20–23, 29, 31, 35–36, 41.
**Current repository state**
Capabilities exist with iteration tests; final combined stress unproved.
**Dependencies**
I06-S01-T02.
**Implementation steps**
1. Run combined 8-hour generic/Spark cardinality, loader churn, writer overload/disk failure, incident and control stress with resource time series.
2. Fuzz config/report/paths; scan all outputs/artifacts/logs for seeded secrets/payloads/messages/literals; test modules/bootstrap/recursive loading.
3. Run JMH and application baselines per JDK/lane, compare to configured release thresholds and document variance/envelope.
**Expected code and file changes**
- hardening scripts/fixtures/tests, benchmark and `TEST-RESULTS.md` evidence.
**Behavioral contract**
- Any app semantic/exit change, unbounded trend, secret leak, verifier error, deadlock, missing evidence, or threshold breach blocks release; feature failure remains isolated.
**Task-level tests**
- `I06-S02-T01-UT01` `scripts/stress-full.sh --hours 8`, extended.
- `I06-S02-T01-UT02` `scripts/security-privacy.sh`, new.
- `I06-S02-T01-UT03` `scripts/benchmark-all.sh --compare-baseline`, extended.
**Acceptance criteria**
- [ ] Bounds hold to steady state. [ ] zero seeded leaks. [ ] no application-thread file I/O. [ ] overhead within documented thresholds.
**Completion evidence**
- Time series, heap/loader deltas, scan reports, benchmark confidence intervals.
**Risks and notes**
- Flaky or unavailable measurements are failures until explained and rerun.

#### I06-S02-T02 — Assemble, reproduce, inspect, and certify release artifacts
**Objective**
Create the exact final artifacts twice, verify contents/checksums/provenance, execute extracted examples, and record release metadata.
**Specification references**
Sections 6.3, 7, 28, 31, 41.
**Current repository state**
Pre-release packaging exists; final archive contract unproved.
**Dependencies**
I06-S02-T01.
**Implementation steps**
1. Build in two clean isolated directories with fixed inputs and compare every artifact byte/hash.
2. Assemble agent, viewer ZIP, complete project ZIP containing viewer, specification, checksum file, notes/changelog, source/Javadoc, schema, benchmarks, SBOM and provenance.
3. Verify signatures/checksums/manifests/licences/dependencies/class55; extract elsewhere and execute every documented demo and viewer test.
**Expected code and file changes**
- release/verification scripts, metadata templates, final evidence and distribution archives.
**Behavioral contract**
- Exact filenames/version; archives contain no credentials/IDE/build caches; checksum file covers every release payload; artifact runtime needs no source tree/network.
**Task-level tests**
- `I06-S02-T02-UT01` `scripts/reproducible-release.sh 0.1.0`, new; two hashes match.
- `I06-S02-T02-UT02` `scripts/verify-release-artifacts.sh dist/0.1.0`, new; content/dependency/checksum/SBOM gates pass.
- `I06-S02-T02-UT03` `scripts/verify-extracted-release.sh dist/0.1.0`, new; all demos/viewer work.
**Acceptance criteria**
- [ ] Every required/recommended artifact present and validated. [ ] full matrix evidence references exact JAR hash. [ ] zero unexplained requirement gaps.
**Completion evidence**
- SHA256SUMS, archive listings, SBOM/provenance, extracted-run logs, final matrix and traceability audit.
**Risks and notes**
- Publishing itself is outside implementation; a release candidate is not complete until remote branch/commit verification required by iteration policy succeeds.

## Cumulative test-suite evolution

| First mandatory | Carried forward suite and exact entry point | Gate |
|---|---|---|
| I01 | `mvn -B -Pgeneric clean verify`; bytecode/artifact/viewer/doc scripts | JDK11 packaged baseline and schema valid |
| I02 | `scripts/test-matrix.sh --generic`; bytecode/loader/module and deep-feature suites | JDK11/17/21, original semantics identical |
| I03 | dump/control/incident/stress/JMH scripts | bounds, privacy and overhead pass |
| I04 | `scripts/test-matrix.sh --spark35`; CSV/SER/standalone | Spark3.5.9 JDK11/17 exact lanes |
| I05 | `scripts/test-full-matrix.sh`; `scripts/test-pyspark.sh --all` | all four Spark and both PySpark environments |
| I06 | browser/no-network/large/XSS, 8-hour stability, reproducibility/extracted release | zero failure or mandatory skip |

## Compatibility and certification matrix by iteration

| Lane | I01 | I02 | I03 | I04 | I05 | I06 |
|---|---|---|---|---|---|---|
| Generic JDK11 | mandatory | M | M | M | M | M |
| Generic JDK17/JDK21 | smoke | mandatory | M | M | M | M |
| Spark3.5.9 JDK11/JDK17 Scala2.12 | — | — | — | mandatory | M | M |
| Spark4.2.0 JDK17/JDK21 Scala2.13 | — | — | — | — | mandatory | M |
| PySpark3.5.9 Python3.8+ / PySpark4.2.0 Python3.10+ | — | — | — | exploratory JVM only | mandatory | M |
| Spark3.5+JDK21 / Spark4+JDK11 | — | — | — | policy test | mandatory policy test | M |

## Artifact, CI, performance, security, documentation, and release evolution

Every iteration emits versioned agent/viewer/example/schema/docs and retains its golden report corpus. CI adds a capability lane in the same iteration and never removes it. Performance begins with microbenchmarks in I03, adds Spark event/cardinality loads in I04/I05, and culminates in per-lane comparisons and 8-hour stability in I06. Security/privacy tests begin with config/path/output/viewer safety in I01, add callback and payload exclusions in I02, dump/control permission tests in I03, Spark/PySpark content redaction in I04/I05, and fuzz/seeded-secret/supply-chain scans in I06. Documentation evolves alongside each public contract; final release generation consumes rather than duplicates configuration/schema metadata.

## Requirement traceability matrix and coverage audit

The detailed matrix is machine-readable in `BUILD-PLAN-TRACEABILITY.json`. Stable requirement IDs are `REQ-S<section>-L<source-line>-N<occurrence>`, so every one of the **221 normative keyword occurrences** is independently auditable. Section mapping assigns each record to one or more tasks and tests; a requirement first becomes available at its mapped task's iteration and becomes fully conformant no later than its mapped release iteration.

| Audit item | Count/status |
|---|---:|
| Normative keyword occurrences identified | 221 |
| Assigned to implementation task(s) | 221 |
| Assigned to verifying test(s) | 221 |
| Intentionally deferred beyond 0.1 | 0 |
| Uncertain interpretations | 0; decisions below resolve planning interpretations |
| Uncovered | 0 |

Keyword occurrence is a conservative audit granularity: repeated `MUST` tokens on one source line receive separate IDs but share the exact source text and mapping. Non-keyword normative tables/defaults are mapped by their owning section references in tasks and contract/schema tests.

## Risk register

| ID | Risk (likelihood/impact) | Affected work | Prevention; detection | Fallback / release block |
|---|---|---|---|---|
| RISK-001 | Bytecode semantic drift H/Critical | I02+ | advice templates; behavior oracle/verifier | original bytes; any mismatch blocks |
| RISK-002 | Constructor/uninitializedThis H/Critical | I02 | post-init outermost token; constructor corpus | disable path; verifier/count mismatch blocks |
| RISK-003 | ATHROW/exception double count H/High | I02/I04 | source-separated guards; rethrow/wrapper tests | degrade source; semantic ambiguity blocks claim |
| RISK-004 | Bootstrap/module visibility M/Critical | I02+ | minimal Java11 bridge; module/loader tests | disable bootstrap path; app impact blocks |
| RISK-005 | Recursive loading/loader retention M/Critical | I02/I04/I05 | no Class.forName, guards, weak/ClassValue caches | fail-open; non-collectable loader blocks |
| RISK-006 | Spark internal signature drift H/High | I04/I05 | centralized per-version catalog/contracts | capability DEGRADED; certified-lane mismatch blocks |
| RISK-007 | Scala binary collision M/Critical | I04/I05 | profile isolation/no bundled Spark | disable Spark; cross-line loading blocks |
| RISK-008 | Py4J lifecycle misattribution H/High | I05 | reconcile/weak identity and GC tests | PARTIAL accuracy; leaks/content capture block |
| RISK-009 | Cardinality/memory retention H/Critical | I01+ | hard caps/other/drop and long stress | throttle/breaker; cap/growth breach blocks |
| RISK-010 | Writer overload/application I/O M/Critical | I01+ | bounded nonblocking deque, thread-tag tests | drop/degrade; app-thread I/O/deadlock blocks |
| RISK-011 | Application overhead H/High | I03+ | budgets/JMH/baselines/adaptive order | throttle; envelope breach blocks |
| RISK-012 | JFR/runtime availability M/Medium | I03 | reflective adapters/fallback matrix | MXBean/UNAVAILABLE; false support blocks |
| RISK-013 | Virtual-thread misinterpretation M/High | I01/I03 | explicit coverage/source | partial/unavailable; false CPU claim blocks |
| RISK-014 | Privacy leakage M/Critical | all | safe defaults/redaction/seed scans/XSS tests | omit field; any seeded leak blocks |
| RISK-015 | Schema evolution/viewer parse H/High | all | golden corpus/additive policy | isolate line/unsupported notice; backward break blocks |
| RISK-016 | Viewer browser security/memory M/Critical | I01/I06 | CSP/escaping/workers/downsampling | reject input; XSS/network/unbounded memory blocks |
| RISK-017 | Unsupported combo misrepresented M/High | I04+ | explicit policy tests | Spark-only UNSUPPORTED; generic regression blocks |
| RISK-018 | Long-run instability M/Critical | I03/I06 | 30-min then 8-hour stress/time series | breaker; sustained growth/deadlock blocks |
| RISK-019 | Reproducibility/supply chain M/High | I01/I06 | pins/timestamps/SBOM/two builds | no release; hash/dependency discrepancy blocks |
| RISK-020 | Path/dump abuse M/Critical | I01/I03 | canonical containment/permissions/limits/fuzz | reject path; escape or unsafe exposure blocks |

## Final MadLava 0.1.0 acceptance checklist

- [ ] One Maven module and one Java-11/major-55 agent JAR; internal bridge only; public ASM relocated; no Spark/test dependencies; at most one primary transformer.
- [ ] All lifecycle, configuration, feature, accuracy, availability, privacy, cardinality, output, control, incident and overhead contracts pass packaged tests.
- [ ] Generic 11/17/21; Spark3.5.9 11/17; Spark4.2.0 17/21; PySpark3.5.9/4.2.0 pass; invalid combos disable Spark only.
- [ ] CSV, Java/Kryo serialization, PySpark and additional failure suites prove correlations without data/message leakage.
- [ ] Viewer is offline, secure, accessible, bounded, schema-compatible and opens all produced reports/incidents.
- [ ] Performance/stability/reproducibility/documentation/extracted-artifact tests pass with no mandatory skip.
- [ ] Required release files, checksums, licence/notices, schema, SBOM/provenance and evidence are validated.
- [ ] Traceability reports 221/221 assigned to implementation and tests, zero unexplained uncovered requirements.

## Plan completeness audit

The roadmap was checked for every specification section, public configuration group, generic/Spark feature, schema envelope/unit, lifecycle and accuracy behavior, privacy default, compatibility lane, negative/failure test, packaged-agent path, viewer requirement, documentation/release artifact, cumulative regression, dependency direction and circular dependency. Tasks compile to small coherent changes; stages end in integrated gates; iterations are distributable. No task depends on a later task. Section 40 post-0.1 enhancements are excluded except foundations already normative elsewhere. Machine validation must additionally assert all task/test IDs referenced by JSON exist here, dependency targets exist and are earlier, and coverage/uncovered counts agree.

## Explicit decisions and assumptions

| ID | Decision/assumption |
|---|---|
| DEC-001 | The absent historical implementation means all section-38 “present” items are planned anew; no conformance is credited without files/tests. |
| DEC-002 | A source line containing a normative keyword is the stable atomic traceability record; multiple occurrences get `N1`, `N2`, preserving all 221 tokens. |
| DEC-003 | Tables/defaults without normative keywords remain contractual through section/task references and schema/config parameterized tests. |
| DEC-004 | Java<11 is tested with an available fixture JVM or bytecode-level harness; it is not a supported matrix lane. |
| DEC-005 | “Spark 4.x” certification anchor is exactly 4.2.0 as specified; other patches are non-blocking additional lanes. |
| DEC-006 | Viewer starts minimal in I01 because every iteration must open its reports; full section-37 UX waits for stable Spark semantics in I06. |
| DEC-007 | Every iteration follows branch `Iteration-<NN>`; after all gates, intended-tree review, commit, push and `git ls-remote` hash equality are completion requirements. |
| DEC-008 | If a mandatory tool/runtime/browser is unavailable, the iteration remains incomplete; evidence may not record it as a skip. |

## Iteration completion evidence template

For each iteration write `evidence/I<NN>/completion.json` containing branch, version, HEAD hash, intended `git status`, task/stage/iteration/cumulative commands and result files, runtime versions, artifact paths/sizes/SHA-256, packaging/schema/viewer/security/performance results, push output, `git ls-remote origin refs/heads/Iteration-<NN>` output and `remoteHashMatchesHead: true`. Only after every gate is green: inspect `git diff --check`, `git status --short`, archive contents and generated evidence; commit with `MadLava <version>: <increment>`; push `git push -u origin Iteration-<NN>`; compare local `git rev-parse HEAD` with remote. Any failure requires a fix and repetition of the full certification before commit/push.
