# I01 testing

Unit tests cover registry state, immutable context, JSON encoding, queue overflow, argument normalization, and hashing. `PackagedAgentIT` launches a real child JVM with the built JAR through `-javaagent`, verifies the target result, and validates final schema-v3 output. Certification also inspects the manifest, archive contents, class-file version, viewer security, checksums, and documentation commands.
