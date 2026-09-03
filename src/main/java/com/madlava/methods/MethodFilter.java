package com.madlava.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Include/exclude filter where excludes always win and empty includes match nothing. */
public final class MethodFilter {
    private final List<MethodPattern> includes;
    private final List<MethodPattern> excludes;

    public MethodFilter(List<MethodPattern> includes, List<MethodPattern> excludes) {
        this.includes = Collections.unmodifiableList(new ArrayList<>(includes));
        this.excludes = Collections.unmodifiableList(new ArrayList<>(excludes));
    }

    public static MethodFilter parse(String includes, String excludes) {
        return new MethodFilter(parsePatterns(includes), parsePatterns(excludes));
    }

    public boolean matches(String owner, String method, String descriptor) {
        if (includes.isEmpty()) {
            return false;
        }
        boolean included = includes.stream().anyMatch(pattern -> pattern.matches(owner, method, descriptor));
        if (!included) {
            return false;
        }
        return excludes.stream().noneMatch(pattern -> pattern.matches(owner, method, descriptor));
    }

    public boolean mayMatchClass(String owner) {
        if (includes.isEmpty()) {
            return false;
        }
        // A conservative check is intentional. Exact method matching still occurs in visitMethod.
        // Reuse the owner's precompiled pattern: class loading is a hot path and must not compile
        // a fresh regex for every include rule and every loaded class.
        return includes.stream().anyMatch(pattern -> pattern.matchesOwner(owner));
    }

    public List<String> includeSources() {
        List<String> values = new ArrayList<>();
        includes.forEach(pattern -> values.add(pattern.source()));
        return Collections.unmodifiableList(values);
    }

    public List<String> excludeSources() {
        List<String> values = new ArrayList<>();
        excludes.forEach(pattern -> values.add(pattern.source()));
        return Collections.unmodifiableList(values);
    }

    private static List<MethodPattern> parsePatterns(String raw) {
        List<MethodPattern> patterns = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return patterns;
        }
        for (String part : MethodRuleList.split(raw)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // (*) is an observation-mode suffix, not part of the method identity. It may
                // appear immediately before an optional #descriptor.
                int hash = trimmed.indexOf('#');
                String identity = hash >= 0 ? trimmed.substring(0, hash) : trimmed;
                String descriptor = hash >= 0 ? trimmed.substring(hash) : "";
                if (identity.endsWith("(*)")) identity = identity.substring(0, identity.length() - 3);
                patterns.add(MethodPattern.compile(identity + descriptor));
            }
        }
        return patterns;
    }
}
