# Integrating Iteration-12 into Iteration-11

Iteration-12 must extend the Iteration-11 tree rather than replace its existing collectors, reporting pipeline, viewer, packaging, compatibility profiles, or release automation.

Merge these vertical paths:

1. Method filter, registry, metrics, probe bridge, and ASM method transformation.
2. Spark serialization plan, bridge, metrics, configuration, and schema-v1 reporting.
3. Agent bootstrap wiring for `methodProfiling` and `sparkSerialization`.
4. Unit tests for transformation and bridge contracts.
5. The `spark35-it` profile and forked-JVM real Spark tests.
6. Documentation and configuration examples.

Do not merge:

- notebooks;
- notebook runners;
- article datasets;
- shadow `org.apache.spark.*` test classes.

Preserve Iteration-11 functionality and add cumulative regression assertions before accepting the branch.
