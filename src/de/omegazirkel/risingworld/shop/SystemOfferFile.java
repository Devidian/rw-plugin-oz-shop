package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;

public final class SystemOfferFile {
    private SystemOfferFile() {
    }

    public static List<ShopOffer> load(Shop plugin, String configuredFileName) {
        Path pluginPath = Paths.get(plugin.getPath() != null ? plugin.getPath() : ".");
        Path offerFile = pluginPath.resolve(configuredFileName == null || configuredFileName.isBlank()
                ? "system-offers.json"
                : configuredFileName);
        Path defaultOfferFile = offerFile.resolveSibling("system-offers.default.json");
        Path exampleOfferFile = offerFile.resolveSibling("system-offer-example.json");

        try {
            createExampleFile(exampleOfferFile);
            if (Files.notExists(offerFile) && Files.exists(defaultOfferFile)) {
                Shop.logger().info("system-offers.json not found, copying from system-offers.default.json...");
                Files.copy(defaultOfferFile, offerFile);
            }
            if (Files.notExists(offerFile)) {
                Shop.logger().warn("System offers file not found: " + offerFile);
                return List.of();
            }
            String json = Files.readString(offerFile, StandardCharsets.UTF_8);
            return parseOffers(json);
        } catch (IOException | IllegalArgumentException ex) {
            Shop.logger().error("Could not load system offers: " + ex.getMessage());
            return List.of();
        }
    }

    private static List<ShopOffer> parseOffers(String json) {
        List<Map<String, Object>> objects = new JsonObjects(json).parseArray();
        List<ShopOffer> offers = new ArrayList<>();
        for (Map<String, Object> object : objects) {
            String id = stringValue(object, "id");
            String itemName = stringValue(object, "itemName");
            int itemVariant = intValue(object, "itemVariant");
            long price = longValue(object, "price");
            if (id.isBlank() || itemName.isBlank() || itemVariant < 0 || price < 0) {
                continue;
            }
            offers.add(ShopService.systemItemOffer(
                    id,
                    itemName,
                    itemVariant,
                    price,
                    stringValue(object, "currency"),
                    booleanValue(object, "enabled", true)));
        }
        return offers;
    }

    private static void createExampleFile(Path exampleOfferFile) throws IOException {
        if (Files.exists(exampleOfferFile)) {
            return;
        }
        StringBuilder json = new StringBuilder("[\n");
        ItemDefinition[] definitions = Definitions.getAllItemDefinitions();
        boolean first = true;
        if (definitions != null) {
            for (ItemDefinition definition : definitions) {
                if (definition == null || definition.name == null || definition.name.isBlank()) {
                    continue;
                }
                int variations = Math.max(1, definition.variations);
                for (int variantIndex = 0; variantIndex < variations; variantIndex++) {
                    Variant variant = definition.getVariant(variantIndex);
                    if (!first) {
                        json.append(",\n");
                    }
                    first = false;
                    String id = definition.name + "." + variantIndex;
                    json.append("  {\n")
                            .append("    \"id\": \"").append(escape(id)).append("\",\n")
                            .append("    \"itemName\": \"").append(escape(definition.name)).append("\",\n")
                            .append("    \"itemVariant\": ").append(variantIndex).append(",\n")
                            .append("    \"price\": 100,\n")
                            .append("    \"currency\": \"\",\n")
                            .append("    \"enabled\": false");
                    if (variant != null && variant.name != null && !variant.name.isBlank()) {
                        json.append(",\n    \"_variantName\": \"").append(escape(variant.name)).append("\"");
                    }
                    json.append("\n  }");
                }
            }
        }
        json.append("\n]\n");
        Files.writeString(exampleOfferFile, json.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stringValue(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static long longValue(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                return -1L;
            }
        }
        return -1L;
    }

    private static int intValue(Map<String, Object> object, String key) {
        long value = longValue(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) value;
    }

    private static boolean booleanValue(Map<String, Object> object, String key, boolean defaultValue) {
        Object value = object.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static final class JsonObjects {
        private final String input;
        private int index;

        JsonObjects(String input) {
            this.input = input == null ? "" : input;
        }

        List<Map<String, Object>> parseArray() {
            skipWhitespace();
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

        private Long parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("Expected JSON value");
            }
            return Long.parseLong(input.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private boolean matches(String expected) {
            return input.regionMatches(index, expected, 0, expected.length());
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
