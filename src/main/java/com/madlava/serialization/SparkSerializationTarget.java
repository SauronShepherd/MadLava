package com.madlava.serialization;

import java.util.Objects;

/** Exact bytecode target and its observation semantics. */
public final class SparkSerializationTarget {
    public enum RootMode {
        ENTRY_ARGUMENT,
        RETURN_VALUE,
        NOT_APPLICABLE
    }

    public enum ByteMode {
        RETURNED_BYTE_BUFFER,
        INPUT_BYTE_BUFFER,
        UNAVAILABLE
    }

    private final int id;
    private final String ownerInternalName;
    private final String methodName;
    private final String descriptor;
    private final SparkSerializationOperation operation;
    private final SparkSerializationLayer layer;
    private final int primaryArgumentIndex;
    private final RootMode rootMode;
    private final ByteMode byteMode;

    public SparkSerializationTarget(
            int id,
            String ownerInternalName,
            String methodName,
            String descriptor,
            SparkSerializationOperation operation,
            SparkSerializationLayer layer,
            int primaryArgumentIndex,
            RootMode rootMode,
            ByteMode byteMode) {
        this.id = id;
        this.ownerInternalName = Objects.requireNonNull(ownerInternalName);
        this.methodName = Objects.requireNonNull(methodName);
        this.descriptor = Objects.requireNonNull(descriptor);
        this.operation = Objects.requireNonNull(operation);
        this.layer = Objects.requireNonNull(layer);
        this.primaryArgumentIndex = primaryArgumentIndex;
        this.rootMode = Objects.requireNonNull(rootMode);
        this.byteMode = Objects.requireNonNull(byteMode);
    }

    public int id() {
        return id;
    }

    public String ownerInternalName() {
        return ownerInternalName;
    }

    public String owner() {
        return ownerInternalName.replace('/', '.');
    }

    public String methodName() {
        return methodName;
    }

    public String descriptor() {
        return descriptor;
    }

    public SparkSerializationOperation operation() {
        return operation;
    }

    public SparkSerializationLayer layer() {
        return layer;
    }

    public int primaryArgumentIndex() {
        return primaryArgumentIndex;
    }

    public RootMode rootMode() {
        return rootMode;
    }

    public ByteMode byteMode() {
        return byteMode;
    }

    public boolean matches(String owner, String name, String candidateDescriptor) {
        return ownerInternalName.equals(owner)
                && methodName.equals(name)
                && descriptor.equals(candidateDescriptor);
    }
}
