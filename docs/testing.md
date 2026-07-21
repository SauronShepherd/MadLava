# Testing and release gates

`mvn -B -Pgeneric clean verify -Drevision=0.1.0` runs unit tests, integration tests, the packaged child-JVM scenario, shading, and archive assembly. Tests cover configuration, registry state, bounded metrics, encoding and queue behavior, JVM collection, instrumentation, runtime I/O and serialization, diagnostics, Spark-safe fallback, and packaged startup/shutdown.

`scripts/certify-i08.ps1` is the final local release entry point. It runs the complete generic build on Java 11, targeted compatibility tests on Java 17 and 21, then inspects the agent manifest, Java 11 bytecode, offline viewer security contract, release artifacts, extracted archives, English documentation, generated reports, and SHA-256 checksums. Missing required JDKs or failed checks are fatal; there are no mandatory skips.

The development model is task → stage → iteration → cumulative regression. Each iteration uses its own `Iteration-NN` branch. Only an intended tree whose required gates pass is committed. Certification is repeated against the committed HEAD, then the branch is pushed and the remote hash must equal the local commit.

Spark and PySpark lanes described in the exhaustive plan are forward-looking unless corresponding adapters, dependencies, environments, and certification scripts exist in the release. Version 0.1 documentation does not claim certification beyond executable gates present in this repository.
