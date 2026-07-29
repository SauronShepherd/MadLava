package com.madlava.reporting;

import java.lang.reflect.Array;
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
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            quote(String.valueOf(value), output);
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Number) {
            Number number = (Number) value;
            if (number instanceof Double && !Double.isFinite(number.doubleValue())) {
                output.append("null");
            } else if (number instanceof Float && !Float.isFinite(number.floatValue())) {
                output.append("null");
            } else {
                output.append(number);
            }
        } else if (value instanceof Map<?, ?>) {
            appendMap((Map<?, ?>) value, output);
        } else if (value instanceof Iterable<?>) {
            appendIterable((Iterable<?>) value, output);
        } else if (value.getClass().isArray()) {
            appendArray(value, output);
        } else {
            quote(String.valueOf(value), output);
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
            quote(String.valueOf(entry.getKey()), output);
            output.append(':');
            append(entry.getValue(), output);
        }
        output.append('}');
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
                        output.append(String.format("\\u%04x", (int) current));
                    } else {
                        output.append(current);
                    }
            }
        }
        output.append('"');
    }
}
