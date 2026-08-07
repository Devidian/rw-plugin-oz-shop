package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** User-editable defaults for newly generated NPC traders. */
public record TraderGeneratorConfig(List<List<String>> clothing, List<String> hats, List<String> maleNames,
                                    List<String> femaleNames) {
    private static final List<String> FALLBACK_MALE_NAMES = List.of("Merchant");
    private static final List<String> FALLBACK_FEMALE_NAMES = List.of("Merchant");
    private static final List<String> DEFAULT_HATS = List.of("felthat", "pilgrimhat", "cowboyhat", "cappy");

    public TraderGeneratorConfig {
        clothing = immutableOutfits(clothing);
        hats = immutableNames(hats, DEFAULT_HATS);
        maleNames = immutableNames(maleNames, FALLBACK_MALE_NAMES);
        femaleNames = immutableNames(femaleNames, FALLBACK_FEMALE_NAMES);
    }

    public static TraderGeneratorConfig load(Path configFile) {
        try {
            Object parsed = new JsonParser(Files.readString(configFile, StandardCharsets.UTF_8)).parse();
            if (!(parsed instanceof Map<?, ?> root)) throw new IllegalArgumentException("Expected object");
            return new TraderGeneratorConfig(outfits(root.get("clothing")), strings(root.get("hats")),
                    names(root, "male"), names(root, "female"));
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Could not load trader generator config: " + ex.getMessage(), ex);
        }
    }

    public List<String> randomOutfit(Random random) {
        if (clothing.isEmpty()) return List.of();
        Set<String> configuredHats = new HashSet<>(hats);
        List<String> outfit = new ArrayList<>();
        for (String garment : clothing.get(random.nextInt(clothing.size()))) {
            if (!configuredHats.contains(garment)) outfit.add(garment);
        }
        if (!hats.isEmpty() && random.nextInt(100) < 25) outfit.add(hats.get(random.nextInt(hats.size())));
        return List.copyOf(outfit);
    }

    public String randomName(boolean male, Random random) {
        List<String> names = male ? maleNames : femaleNames;
        return names.get(random.nextInt(names.size()));
    }

    private static List<List<String>> outfits(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<List<String>> result = new ArrayList<>();
        for (Object outfit : values) {
            if (!(outfit instanceof List<?> garments)) continue;
            List<String> normalized = new ArrayList<>();
            for (Object garment : garments) if (garment instanceof String name && !name.isBlank()) normalized.add(name.trim());
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private static List<String> names(Map<?, ?> root, String gender) {
        Object names = root.get("names");
        return names instanceof Map<?, ?> values ? strings(values.get(gender)) : List.of();
    }

    private static List<String> strings(Object values) {
        if (!(values instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : list) if (value instanceof String name && !name.isBlank()) result.add(name.trim());
        return result;
    }

    private static List<List<String>> immutableOutfits(List<List<String>> outfits) {
        if (outfits == null) return List.of();
        return outfits.stream().filter(Objects::nonNull).map(List::copyOf).toList();
    }

    private static List<String> immutableNames(List<String> names, List<String> fallback) {
        if (names == null || names.isEmpty()) return fallback;
        return List.copyOf(names);
    }

    private static final class JsonParser {
        private final String input;
        private int index;

        JsonParser(String input) { this.input = input == null ? "" : input; }

        Object parse() {
            Object value = value();
            whitespace();
            if (index != input.length()) throw error("Unexpected input");
            return value;
        }

        private Object value() {
            whitespace();
            if (peek('{')) return object();
            if (peek('[')) return array();
            if (peek('"')) return string();
            throw error("Expected JSON value");
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            whitespace();
            if (peek('}')) { index++; return result; }
            while (true) {
                String key = string();
                expect(':');
                result.put(key, value());
                whitespace();
                if (peek(',')) { index++; continue; }
                expect('}');
                return result;
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (peek(']')) { index++; return result; }
            while (true) {
                result.add(value());
                whitespace();
                if (peek(',')) { index++; continue; }
                expect(']');
                return result;
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') return result.toString();
                if (character != '\\') { result.append(character); continue; }
                if (index >= input.length()) throw error("Unterminated escape");
                char escaped = input.charAt(index++);
                result.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    default -> throw error("Unsupported escape");
                });
            }
            throw error("Unterminated string");
        }

        private void expect(char expected) {
            whitespace();
            if (!peek(expected)) throw error("Expected '" + expected + "'");
            index++;
        }

        private void whitespace() { while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++; }
        private boolean peek(char expected) { return index < input.length() && input.charAt(index) == expected; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at offset " + index); }
    }
}
