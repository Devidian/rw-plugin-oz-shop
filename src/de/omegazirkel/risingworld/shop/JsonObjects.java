package de.omegazirkel.risingworld.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the historical flat object-array formats used by zones and offers. */
final class JsonObjects {
        private final String input;
    private int index;
    private final boolean zoneMode;

    JsonObjects(String input, boolean zoneMode) {
        this.input = input == null ? "" : input;
        this.zoneMode = zoneMode;
    }

    List<Map<String, Object>> parseArray() {
        skipWhitespace();
        if (zoneMode && index >= input.length()) {
            return List.of();
        }
        expect('[');
        List<Map<String, Object>> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            result.add(parseObject());
            skipWhitespace();
            if (peek(',')) {
                index++;
                continue;
            }
            expect(']');
            return result;
        }
    }

    private Map<String, Object> parseObject() {
        skipWhitespace();
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return result;
        }
        while (true) {
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            if (peek(',')) {
                index++;
                continue;
            }
            expect('}');
            return result;
        }
    }

    private Object parseValue() {
        skipWhitespace();
        if (peek('"')) {
            return parseString();
        }
        if (matches("true")) {
            index += 4;
            return Boolean.TRUE;
        }
        if (matches("false")) {
            index += 5;
            return Boolean.FALSE;
        }
        if (matches("null")) {
            index += 4;
            return null;
        }
        return parseNumber();
    }

    private String parseString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < input.length()) {
            char ch = input.charAt(index++);
            if (ch == '"') {
                return result.toString();
            }
            if (ch == '\\') {
                if (index >= input.length()) {
                    throw error("Unexpected end of escape sequence");
                }
                char escaped = input.charAt(index++);
                result.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> escaped;
                });
            } else {
                result.append(ch);
            }
        }
        throw error("Unterminated string");
    }

    private Number parseNumber() {
        int start = index;
        if (peek('-')) index++;
        while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
        if (zoneMode) {
            if (start == index || (input.charAt(start) == '-' && start + 1 == index)) {
                throw error("Expected number");
            }
            return Long.parseLong(input.substring(start, index));
        }
        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
        }
        if (start == index) throw error("Expected JSON value");
        String number = input.substring(start, index);
        return decimal ? Double.parseDouble(number) : Long.parseLong(number);
    }

    private void expect(char expected) {
        skipWhitespace();
        if (index >= input.length() || input.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char expected) {
        return index < input.length() && input.charAt(index) == expected;
    }

    private boolean matches(String value) {
        return input.regionMatches(index, value, 0, value.length());
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + (zoneMode ? " at index " : " at offset ") + index);
    }
}
