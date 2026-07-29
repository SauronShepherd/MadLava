package com.madlava.methods;

import java.util.*;

/** Immutable value key for rendered argument tuples. */
public final class ArgumentKey {
    private final List<String> arguments;
    public ArgumentKey(List<String> arguments) { this.arguments = List.copyOf(arguments == null ? List.of() : arguments); }
    public List<String> arguments() { return arguments; }
    @Override public boolean equals(Object other) { return other instanceof ArgumentKey && arguments.equals(((ArgumentKey) other).arguments); }
    @Override public int hashCode() { return arguments.hashCode(); }
}
