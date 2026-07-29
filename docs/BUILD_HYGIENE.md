# Build hygiene

The unreleased profiler build uses the project artifact version `0.1.0`. Maven therefore emits:

```text
target/madlava-agent-0.1.0.jar
```

The Spark integration tests resolve the agent path through `${project.build.finalName}` rather than duplicating the version in profile configuration.

## Warning cleanup

The shaded agent relocates ASM into MadLava's private namespace. ASM dependency manifests and JPMS `module-info.class` descriptors are excluded because they no longer describe the relocated shaded JAR. MadLava's own agent manifest remains in place.

Spark 3.5's transitive Hadoop dependency graph selects the legacy `org.apache.yetus:audience-annotations:0.5.0` descriptor. That old POM causes Maven effective-model warnings. The Spark 3.5 test profiles exclude it and provide the compatible current annotation artifact explicitly at `0.15.1`. This dependency is test-scoped and is not packaged into the MadLava agent.

The Maven `skip non existing resourceDirectory` lines are informational messages, not warnings, and do not affect the artifact.
