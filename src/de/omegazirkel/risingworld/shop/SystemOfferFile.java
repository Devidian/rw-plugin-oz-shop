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
import net.risingworld.api.definitions.Crafting.Recipe;
import net.risingworld.api.definitions.Crafting.Recipe.Ingredient;
import net.risingworld.api.definitions.Crafting.CraftingStation;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;

public final class SystemOfferFile {
    private SystemOfferFile() {
    }

    public static List<ShopOffer> load(Shop plugin, String configuredFileName, boolean generateDefinitionExports) {
        Path pluginPath = Paths.get(plugin.getPath() != null ? plugin.getPath() : ".");
        Path offerFile = pluginPath.resolve(configuredFileName == null || configuredFileName.isBlank()
                ? "system-offers.json"
                : configuredFileName);
        Path defaultOfferFile = offerFile.resolveSibling("system-offers.default.json");
        Path exportOfferFile = offerFile.resolveSibling("system-offer-export.json");
        Path recipeExportFile = offerFile.resolveSibling("system-recipes-export.json");

        try {
            if (generateDefinitionExports) {
                createOfferExportFile(exportOfferFile);
                createRecipeExportFile(recipeExportFile);
            }
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
            int amount = intValue(object, "amount");
            long legacyPrice = longValue(object, "price");
            double basePrice = doubleValue(object, "basePrice");
            long buyPrice = longValue(object, "buyPrice");
            long sellPrice = longValue(object, "sellPrice");
            if (sellPrice < 0 && legacyPrice >= 0) {
                sellPrice = legacyPrice;
            }
            if (basePrice < 0.0d && legacyPrice >= 0 && amount > 0) {
                basePrice = (double) legacyPrice / amount;
            }
            if (buyPrice < 0 && basePrice >= 0.0d) {
                buyPrice = Math.round(basePrice * amount);
            }
            if (sellPrice < 0 && basePrice >= 0.0d) {
                sellPrice = Math.round(basePrice * amount);
            }
            Items.ItemDefinition def = Definitions.getItemDefinition(itemName);
            if (def == null || id.isBlank() || itemName.isBlank() || itemVariant < 0 || amount <= 0
                    || basePrice < 0.0d || buyPrice < 0 || sellPrice < 0) {
                Shop.logger().warn("Invalid offer" + (def == null ? " definition not found" : "")
                        + (itemName.isBlank() ? " no itemName set" : "<itemName:" + itemName + ">"));
                continue;
            }
            boolean legacyEnabled = booleanValue(object, "enabled", false);
            offers.add(ShopService.systemItemOffer(
                    id,
                    itemName,
                    itemVariant,
                    amount,
                    basePrice,
                    buyPrice,
                    sellPrice,
                    stringValue(object, "currency"),
                    booleanValue(object, "buyEnabled", false),
                    booleanValue(object, "sellEnabled", legacyEnabled)));
        }
        return offers;
    }

    private static void createOfferExportFile(Path exportOfferFile) throws IOException {
        if (Files.exists(exportOfferFile)) {
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
                            .append("    \"amount\": 1,\n")
                            .append("    \"basePrice\": 100,\n")
                            .append("    \"buyPrice\": 100,\n")
                            .append("    \"sellPrice\": 250,\n")
                            .append("    \"currency\": \"\",\n")
                            .append("    \"sellEnabled\": false,\n")
                            .append("    \"buyEnabled\": false");
                    if (variant != null && variant.name != null && !variant.name.isBlank()) {
                        json.append(",\n    \"_variantName\": \"").append(escape(variant.name)).append("\"");
                    }
                    json.append("\n  }");
                }
            }
        }
        json.append("\n]\n");
        Files.writeString(exportOfferFile, json.toString(), StandardCharsets.UTF_8);
    }

    private static void createRecipeExportFile(Path recipeExportFile) throws IOException {
        if (Files.exists(recipeExportFile)) {
            return;
        }
        StringBuilder json = new StringBuilder("[\n");
        Recipe[] recipes = Definitions.getAllRecipes();
        boolean first = true;
        if (recipes != null) {
            for (Recipe recipe : recipes) {
                if (recipe == null || recipe.name == null || recipe.name.isBlank()) {
                    continue;
                }
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                json.append("  {\n")
                        .append("    \"id\": ").append(recipe.id).append(",\n")
                        .append("    \"name\": \"").append(escape(recipe.name)).append("\",\n")
                        .append("    \"type\": \"").append(escape(string(recipe.type))).append("\",\n")
                        .append("    \"amount\": ").append(recipe.amount).append(",\n")
                        .append("    \"itemName\": \"").append(escape(recipe.itemDef == null ? "" : recipe.itemDef.name)).append("\",\n")
                        .append("    \"itemTypeId\": ").append(recipe.itemDef == null ? 0 : recipe.itemDef.id).append(",\n")
                        .append("    \"texture\": ").append(recipe.texture).append(",\n")
                        .append("    \"category\": \"").append(escape(string(recipe.category))).append("\",\n")
                        .append("    \"subCategory\": \"").append(escape(string(recipe.subCategory))).append("\",\n")
                        .append("    \"hasVariants\": ").append(recipe.hasVariants).append(",\n")
                        .append("    \"parent\": \"").append(escape(recipe.parent == null ? "" : recipe.parent.name)).append("\",\n")
                        .append("    \"ingredients\": [");
                appendIngredients(json, recipe.ingredients);
                json.append("\n    ],\n")
                        .append("    \"requiredCraftingStations\": [");
                appendCraftingStations(json, recipe.requiredCraftingStations);
                json.append("\n    ],\n")
                        .append("    \"_rawIngredients\": \"").append(escape(recipe.rawingredientstring)).append("\",\n")
                        .append("    \"_rawCraftingStations\": \"").append(escape(recipe.rawworkbenchesstring)).append("\"\n")
                        .append("  }");
            }
        }
        json.append("\n]\n");
        Files.writeString(recipeExportFile, json.toString(), StandardCharsets.UTF_8);
    }

    private static void appendIngredients(StringBuilder json, Ingredient[] ingredients) {
        if (ingredients == null || ingredients.length == 0) {
            return;
        }
        boolean first = true;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            json.append(first ? "\n" : ",\n")
                    .append("      {\n")
                    .append("        \"itemName\": \"").append(escape(ingredient.itemDef == null ? "" : ingredient.itemDef.name)).append("\",\n")
                    .append("        \"itemTypeId\": ").append(ingredient.itemDef == null ? 0 : ingredient.itemDef.id).append(",\n")
                    .append("        \"group\": \"").append(escape(string(ingredient.group))).append("\",\n")
                    .append("        \"count\": ").append(ingredient.count).append(",\n")
                    .append("        \"texture\": ").append(ingredient.texture).append(",\n")
                    .append("        \"consume\": ").append(ingredient.consume).append("\n")
                    .append("      }");
            first = false;
        }
    }

    private static void appendCraftingStations(StringBuilder json, CraftingStation[] stations) {
        if (stations == null || stations.length == 0) {
            return;
        }
        boolean first = true;
        for (CraftingStation station : stations) {
            if (station == null) {
                continue;
            }
            json.append(first ? "\n" : ",\n")
                    .append("      {\n")
                    .append("        \"id\": ").append(station.id).append(",\n")
                    .append("        \"name\": \"").append(escape(station.name)).append("\",\n")
                    .append("        \"mainType\": \"").append(escape(string(station.maintype))).append("\",\n")
                    .append("        \"type\": \"").append(escape(string(station.type))).append("\"\n")
                    .append("      }");
            first = false;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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

    private static double doubleValue(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ex) {
                return -1.0d;
            }
        }
        return -1.0d;
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

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw error("Expected JSON value");
            }
            String number = input.substring(start, index);
            return decimal ? Double.parseDouble(number) : Long.parseLong(number);
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
