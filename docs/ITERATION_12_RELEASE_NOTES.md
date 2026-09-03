# Iteration-12 release notes

## Added

- Complete generic JVM method tracing.
- Spark Java/Kryo serialization analysis with exact descriptor matching.
- Runtime Spark, Scala, and Java identity in schema-v1 reports.
- Spark 3.5.x support for Scala 2.12 and 2.13.
- Spark 4.x support for Scala 2.13.
- Forked-agent certification profiles for Spark 3.5.9, 4.0.4, 4.1.3, and 4.2.0.
- Java 11/17 certification for Spark 3.5 and Java 17/21 certification for Spark 4.
- GitHub Actions matrix and local matrix runner.

## Compatibility rule

The 0.1.0 certification matrix includes Spark 3.5.9 (Scala 2.12/2.13), Spark 4.0.4, and Spark 4.1.3 on Java 17. PySpark Arrow smoke certification is limited to PySpark 4.0.1 with Python 3.11, PyArrow 15.0.2, and Pandas 2.0.3. PySpark 3.5.x Arrow conversion remains an explicit limitation of this release candidate.

The adapter matches exact JVM owners, names, and descriptors. New patch releases are not silently assumed compatible: their profile must pass target coverage, real round trips, exceptional-path assertions, and JSONL checks.

## Scope boundary

Article notebooks are not part of MadLava and are not used as tests.
