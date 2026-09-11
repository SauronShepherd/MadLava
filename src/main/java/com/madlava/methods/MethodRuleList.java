package com.madlava.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Splits the compact semicolon-delimited method-rule form without breaking JVM object descriptors. */
public final class MethodRuleList {
    private MethodRuleList() { }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean descriptor = false;
        boolean objectType = false;
        for (int index = 0; index < raw.length(); index++) {
            char c = raw.charAt(index);
            if (c == '#' && !descriptor) descriptor = true;
            if (descriptor && !objectType && c == 'L') objectType = true;

            if (c == ';') {
                if (descriptor && objectType) {
                    current.append(c);
                    objectType = false;
                    continue;
                }
                add(result, current);
                current.setLength(0);
                descriptor = false;
                objectType = false;
                continue;
            }
            current.append(c);
        }
        add(result, current);
        return Collections.unmodifiableList(result);
    }

    private static void add(List<String> result, StringBuilder value) {
        String trimmed = value.toString().trim();
        if (!trimmed.isEmpty()) result.add(trimmed);
    }
}
