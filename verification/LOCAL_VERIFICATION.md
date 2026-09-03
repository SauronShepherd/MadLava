# Verification status

This package defines automated certification lanes for:

- Spark 3.5.9 / Scala 2.12 / Java 11 and 17;
- Spark 3.5.9 / Scala 2.13 / Java 11 and 17;
- Spark 4.0.4 / Scala 2.13 / Java 17 and 21;
- Spark 4.1.3 / Scala 2.13 / Java 17 and 21;
- Spark 4.2.0 / Scala 2.13 / Java 17 and 21.

This container has Java 21 but no Maven and cannot resolve Maven artifacts, so the real-Spark lanes were not executed here. The package includes Maven Enforcer rules, fresh-child-JVM tests, runtime version assertions, and a GitHub Actions matrix. Run:

```bash
scripts/verify-iteration12.sh
```

before merging Iteration-12. No claim is made here that unavailable external-dependency lanes passed in this container.
