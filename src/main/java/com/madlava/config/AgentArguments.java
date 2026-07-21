package com.madlava.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentArguments {
    private AgentArguments() {}

    public static Map<String, String> parse(String input) {
        if (input == null || input.isBlank()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i <= input.length(); i++) {
            char c = i == input.length() ? ',' : input.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) { add(result, token.toString()); token.setLength(0); }
            else token.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted agent argument");
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, String> result, String token) {
        int split = token.indexOf('=');
        if (split <= 0) throw new IllegalArgumentException("Agent arguments must use key=value syntax");
        String key = token.substring(0, split).trim();
        String value = token.substring(split + 1).trim();
        if (key.isEmpty() || result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate or empty agent argument: " + key);
    }
}
