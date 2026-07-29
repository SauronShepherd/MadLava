package com.madlava.methods;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Stable, payload-free identity of an instrumented JVM method. */
public final class MethodKey {
    private final String loaderScope;
    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodKey(String loaderScope, String owner, String name, String descriptor) {
        this.loaderScope = sanitize(loaderScope, "unknown-loader");
        this.owner = sanitize(owner, "unknown-owner");
        this.name = sanitize(name, "unknown-method");
        this.descriptor = sanitize(descriptor, "unknown-descriptor");
    }

    public String loaderScope() {
        return loaderScope;
    }

    public String owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String descriptor() {
        return descriptor;
    }

    public Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("loaderScope", loaderScope);
        report.put("owner", owner);
        report.put("method", name);
        report.put("descriptor", descriptor);
        return report;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MethodKey)) {
            return false;
        }
        MethodKey that = (MethodKey) other;
        return loaderScope.equals(that.loaderScope)
                && owner.equals(that.owner)
                && name.equals(that.name)
                && descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loaderScope, owner, name, descriptor);
    }

    @Override
    public String toString() {
        return loaderScope + ':' + owner + '.' + name + descriptor;
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
