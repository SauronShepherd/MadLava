package com.madlava.reporting;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/** Minimal dependency-free JSON encoder for bounded diagnostic snapshots. */
public final class Json {
    private Json() {
    }

    public static String encode(Object value) {
        StringBuilder output = new StringBuilder(4_096);
        append(value, output);
        return output.toString();
    }

    private static void append(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            quote((String) value, output);
        } else if (value instanceof Character) {
            quote(Character.toString((Character) value), output);
        } else if (value instanceof Enum<?>) {
            // Enum.toString() is overridable. name() is final and cannot execute application code.
            quote(((Enum<?>) value).name(), output);
        } else if (value instanceof Boolean) {
            output.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (isKnownNumber(value)) {
            appendKnownNumber((Number) value, output);
        } else if (value instanceof Number) {
            // Number.toString() is application-controlled for custom Number implementations.
            // Diagnostic encoding must never invoke it.
            quote(typeMarker(value), output);
        } else if (value instanceof Map<?, ?> && isTrustedContainer(value.getClass())) {
            appendMap((Map<?, ?>) value, output);
        } else if (value instanceof Iterable<?> && isTrustedContainer(value.getClass())) {
            appendIterable((Iterable<?>) value, output);
        } else if (value.getClass().isArray()) {
            appendArray(value, output);
        } else {
            // Do not call arbitrary application toString() implementations from the profiler.
            quote(typeMarker(value), output);
        }
    }

    private static boolean isKnownNumber(Object value) {
        Class<?> type = value.getClass();
        return type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class;
    }

    private static void appendKnownNumber(Number number, StringBuilder output) {
        Class<?> type = number.getClass();
        if (type == Double.class) {
            double value = number.doubleValue();
            output.append(Double.isFinite(value) ? Double.toString(value) : "null");
        } else if (type == Float.class) {
            float value = number.floatValue();
            output.append(Float.isFinite(value) ? Float.toString(value) : "null");
        } else if (type == Byte.class) {
            output.append(Byte.toString(number.byteValue()));
        } else if (type == Short.class) {
            output.append(Short.toString(number.shortValue()));
        } else if (type == Integer.class) {
            output.append(Integer.toString(number.intValue()));
        } else if (type == Long.class) {
            output.append(Long.toString(number.longValue()));
        } else if (type == BigInteger.class) {
            output.append(((BigInteger) number).toString());
        } else if (type == BigDecimal.class) {
            output.append(((BigDecimal) number).toString());
        } else {
            // Defensive fallback; isKnownNumber() should make this unreachable.
            quote(typeMarker(number), output);
        }
    }

    private static void appendMap(Map<?, ?> map, StringBuilder output) {
        output.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (!first) {
                output.append(',');
            }
            first = false;
            quote(safeKey(entry.getKey()), output);
            output.append(':');
            append(entry.getValue(), output);
        }
        output.append('}');
    }

    private static String safeKey(Object key) {
        if (key == null) return "null";
        if (key instanceof String) return (String) key;
        if (key instanceof Character) return Character.toString((Character) key);
        if (key instanceof Enum<?>) return ((Enum<?>) key).name();
        if (isKnownNumber(key)) {
            StringBuilder value = new StringBuilder();
            appendKnownNumber((Number) key, value);
            return value.toString();
        }
        return typeMarker(key);
    }

    private static void appendIterable(Iterable<?> iterable, StringBuilder output) {
        output.append('[');
        Iterator<?> iterator = iterable.iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                output.append(',');
            }
            first = false;
            append(iterator.next(), output);
        }
        output.append(']');
    }

    private static void appendArray(Object array, StringBuilder output) {
        output.append('[');
        int length = Array.getLength(array);
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                output.append(',');
            }
            append(Array.get(array, index), output);
        }
        output.append(']');
    }

    private static boolean isTrustedContainer(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.util.") || name.startsWith("java.util.concurrent.");
    }

    private static String typeMarker(Object value) {
        return "<" + value.getClass().getName() + ">";
    }

    private static void quote(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"':
                    output.append("\\\"");
                    break;
                case '\\':
                    output.append("\\\\");
                    break;
                case '\b':
                    output.append("\\b");
                    break;
                case '\f':
                    output.append("\\f");
                    break;
                case '\n':
                    output.append("\\n");
                    break;
                case '\r':
                    output.append("\\r");
                    break;
                case '\t':
                    output.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        appendUnicodeEscape(current, output);
                    } else {
                        output.append(current);
                    }
            }
        }
        output.append('"');
    }

    private static void appendUnicodeEscape(char value, StringBuilder output) {
        final char[] hex = "0123456789abcdef".toCharArray();
        output.append("\\u")
                .append(hex[(value >>> 12) & 0xF])
                .append(hex[(value >>> 8) & 0xF])
                .append(hex[(value >>> 4) & 0xF])
                .append(hex[value & 0xF]);
    }
}
