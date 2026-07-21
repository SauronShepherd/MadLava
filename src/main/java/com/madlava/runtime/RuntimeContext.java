package com.madlava.runtime;

import com.madlava.core.FeatureRegistry;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

public final class RuntimeContext {
    private final Instrumentation instrumentation;
    private final Clock clock;
    private final Path outputDirectory;
    private final FeatureRegistry featureRegistry;

    public RuntimeContext(Instrumentation instrumentation, Clock clock, Path outputDirectory, FeatureRegistry featureRegistry) {
        this.instrumentation = Objects.requireNonNull(instrumentation);
        this.clock = Objects.requireNonNull(clock);
        this.outputDirectory = Objects.requireNonNull(outputDirectory).toAbsolutePath().normalize();
        this.featureRegistry = Objects.requireNonNull(featureRegistry);
    }
    public Instrumentation instrumentation() { return instrumentation; }
    public Clock clock() { return clock; }
    public Path outputDirectory() { return outputDirectory; }
    public FeatureRegistry featureRegistry() { return featureRegistry; }
}
