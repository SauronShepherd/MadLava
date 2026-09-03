package com.madlava.methods;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Glob-style method pattern.
 *
 * <p>Syntax: {@code fully.qualified.Class.method} with optional JVM descriptor
 * after {@code #}. Asterisks match any sequence of characters. Examples:</p>
 * <ul>
 *   <li>{@code org.apache.spark.serializer.*.*}</li>
 *   <li>{@code org.apache.spark.serializer.KryoSerializerInstance.serialize}</li>
 *   <li>{@code fixtures.SampleTarget.overloaded#(I)I}</li>
 * </ul>
 */
public final class MethodPattern {
    private final String source;
    private final Pattern owner;
    private final Pattern method;
    private final Pattern descriptor;

    private MethodPattern(String source, Pattern owner, Pattern method, Pattern descriptor) {
        this.source = source;
        this.owner = owner;
        this.method = method;
        this.descriptor = descriptor;
    }

    public static MethodPattern compile(String source) {
        Objects.requireNonNull(source, "source");
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Method pattern cannot be empty");
        }

        String identity = trimmed;
        String descriptorText = "*";
        int descriptorSeparator = trimmed.indexOf('#');
        if (descriptorSeparator >= 0) {
            identity = trimmed.substring(0, descriptorSeparator).trim();
            descriptorText = trimmed.substring(descriptorSeparator + 1).trim();
            if (descriptorText.isEmpty())
                throw new IllegalArgumentException("Method descriptor after # cannot be empty: " + source);
            if (descriptorText.indexOf('*') < 0 && descriptorText.indexOf('?') < 0
                    && !MethodRuleParser.validMethodDescriptor(descriptorText))
                throw new IllegalArgumentException("Invalid method descriptor: " + descriptorText);
        }

        int methodSeparator = identity.lastIndexOf('.');
        if (methodSeparator <= 0 || methodSeparator == identity.length() - 1) {
            throw new IllegalArgumentException(
                    "Method pattern must use fully.qualified.Class.method syntax: " + source);
        }

        String ownerText = identity.substring(0, methodSeparator);
        String methodText = identity.substring(methodSeparator + 1);
        return new MethodPattern(
                trimmed,
                Pattern.compile(glob(ownerText)),
                Pattern.compile(glob(methodText)),
                Pattern.compile(glob(descriptorText)));
    }

    public boolean matches(String ownerName, String methodName, String methodDescriptor) {
        return owner.matcher(ownerName).matches()
                && method.matcher(methodName).matches()
                && descriptor.matcher(methodDescriptor).matches();
    }

    /** Fast class-level prefilter using the already-compiled owner expression. */
    public boolean matchesOwner(String ownerName) {
        return owner.matcher(ownerName).matches();
    }

    public String source() {
        return source;
    }

    private static String glob(String value) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '*') {
                regex.append(".*");
            } else if (current == '?') {
                regex.append('.');
            } else {
                if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return regex.append('$').toString();
    }
}
