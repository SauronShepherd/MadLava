package com.madlava.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON parser used only for the bounded agent configuration document. */
final class SimpleJsonParser {
    private final String input;
    private int index;

    private SimpleJsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("JSON input cannot be null");
        }
        SimpleJsonParser parser = new SimpleJsonParser(input);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.finished()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object value() {
        whitespace();
        if (finished()) {
            throw error("Unexpected end of JSON");
        }
        char current = input.charAt(index);
        switch (current) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                literal("true");
                return Boolean.TRUE;
            case 'f':
                literal("false");
                return Boolean.FALSE;
            case 'n':
                literal("null");
                return null;
            default:
                if (current == '-' || Character.isDigit(current)) {
                    return number();
                }
                throw error("Unexpected character '" + current + "'");
        }
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> values = new LinkedHashMap<>();
        whitespace();
        if (peek('}')) {
            index++;
            return values;
        }
        while (true) {
            whitespace();
            if (!peek('"')) {
                throw error("Object key must be a string");
            }
            String key = string();
            if (values.containsKey(key)) {
                throw error("Duplicate object key '" + key + "'");
            }
            whitespace();
            expect(':');
            values.put(key, value());
            whitespace();
            if (peek('}')) {
                index++;
                return values;
            }
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> values = new ArrayList<>();
        whitespace();
        if (peek(']')) {
            index++;
            return values;
        }
        while (true) {
            values.add(value());
            whitespace();
            if (peek(']')) {
                index++;
                return values;
            }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder output = new StringBuilder();
        while (!finished()) {
            char current = input.charAt(index++);
            if (current == '"') {
                return output.toString();
            }
            if (current != '\\') {
                if (current < 0x20) {
                    throw error("Control character in string");
                }
                output.append(current);
                continue;
            }
            if (finished()) {
                throw error("Incomplete escape sequence");
            }
            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"': output.append('"'); break;
                case '\\': output.append('\\'); break;
                case '/': output.append('/'); break;
                case 'b': output.append('\b'); break;
                case 'f': output.append('\f'); break;
                case 'n': output.append('\n'); break;
                case 'r': output.append('\r'); break;
                case 't': output.append('\t'); break;
                case 'u': output.append(unicode()); break;
                default: throw error("Unsupported escape sequence \\" + escaped + "'");
            }
        }
        throw error("Unterminated string");
    }

    private char unicode() {
        if (index + 4 > input.length()) {
            throw error("Incomplete unicode escape");
        }
        String digits = input.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(digits, 16);
        } catch (NumberFormatException failure) {
            throw error("Invalid unicode escape: " + digits);
        }
    }

    private Number number() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        digits();
        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            digits();
        }
        if (peek('e') || peek('E')) {
            decimal = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            digits();
        }
        String text = input.substring(start, index);
        try {
            if (decimal) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException failure) {
            throw error("Invalid number: " + text);
        }
    }

    private void digits() {
        int start = index;
        while (!finished() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error("Expected a digit");
        }
    }

    private void literal(String expected) {
        if (!input.startsWith(expected, index)) {
            throw error("Expected " + expected);
        }
        index += expected.length();
    }

    private void expect(char expected) {
        whitespace();
        if (finished() || input.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char candidate) {
        return !finished() && input.charAt(index) == candidate;
    }

    private void whitespace() {
        while (!finished() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private boolean finished() {
        return index >= input.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + index);
    }
}
