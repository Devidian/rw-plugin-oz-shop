package de.omegazirkel.risingworld.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Crafting.Recipe;
import net.risingworld.api.definitions.Crafting.Recipe.Ingredient;
import net.risingworld.api.definitions.Crafting.CraftingStation;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.definitions.Plants.PlantDefinition;

public final class SystemOfferFile {
    private SystemOfferFile() {
    }

    public static List<ShopOffer> load(Shop plugin, String configuredFileName, boolean generateDefinitionExports) {
        return load(plugin, configuredFileName, generateDefinitionExports, "");
    }

    public static List<ShopOffer> load(Shop plugin, String configuredFileName, boolean generateDefinitionExports,
            String systemShopCurrency) {
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
            return parseOffers(json, systemShopCurrency);
        } catch (IOException | IllegalArgumentException ex) {
            Shop.logger().error("Could not load system offers: " + ex.getMessage());
            return List.of();
        }
    }

    private static List<ShopOffer> parseOffers(String json, String systemShopCurrency) {
        List<Map<String, Object>> objects = new JsonObjects(json).parseArray();
        List<ShopOffer> offers = new ArrayList<>();
        String centralCurrency = systemShopCurrency == null ? "" : systemShopCurrency.trim().toUpperCase();
        for (Map<String, Object> object : objects) {
            String id = stringValue(object, "id");
            String itemName = stringValue(object, "itemName");
            int itemVariant = intValue(object, "itemVariant");
            int amount = intValue(object, "amount");
            long legacyPrice = longValue(object, "price");
            double basePrice = doubleValue(object, "basePrice");
            long buyPrice = longValue(object, "buyPrice");
            long sellPrice = longValue(object, "sellPrice");
            long stock = longValue(object, "stock");
            long targetStock = longValue(object, "targetStock");
            long stockLimit = longValue(object, "stockLimit");
            double drainRate = doubleValue(object, "drainRate");
            double refillRate = doubleValue(object, "refillRate");
            ShopStockMode stockMode = ShopStockMode.from(stringValue(object, "stockMode"));
            double minPriceMultiplier = doubleValue(object, "minPriceMultiplier");
            double maxPriceMultiplier = doubleValue(object, "maxPriceMultiplier");
            double spreadPercent = doubleValue(object, "spreadPercent");
            double drainPercent = doubleValue(object, "drainPercent");
            long drainMax = longValue(object, "drainMax");
            double restockPercent = doubleValue(object, "restockPercent");
            long restockMax = longValue(object, "restockMax");
            long perPlayerDailySellLimit = longValue(object, "perPlayerDailySellLimit");
            long globalDailySellLimit = longValue(object, "globalDailySellLimit");
            if (sellPrice < 0 && legacyPrice >= 0) {
                sellPrice = legacyPrice;
            }
            if (basePrice < 0.0d && legacyPrice >= 0 && amount > 0) {
                basePrice = (double) legacyPrice / amount;
            }
            if (buyPrice < 0 && basePrice >= 0.0d) {
                buyPrice = (long) Math.floor(basePrice * amount);
            }
            if (sellPrice < 0 && basePrice >= 0.0d) {
                sellPrice = (long) Math.ceil(basePrice * amount);
            }
            boolean knownDefinition = isKnownOfferDefinition(itemName);
            if (!knownDefinition || id.isBlank() || itemName.isBlank() || itemVariant < 0 || amount <= 0
                    || basePrice < 0.0d || buyPrice < 0 || sellPrice < 0) {
                Shop.logger().warn("Invalid offer" + (!knownDefinition ? " definition not found" : "")
                        + (itemName.isBlank() ? " no itemName set" : "<itemName:" + itemName + ">"));
                continue;
            }
            boolean enabled = enabledValue(object);
            offers.add(ShopService.systemItemOffer(
                    id,
                    itemName,
                    itemVariant,
                    amount,
                    basePrice,
                    buyPrice,
                    sellPrice,
                    centralCurrency.isBlank() ? stringValue(object, "currency") : centralCurrency,
                    enabled,
                    Math.max(0L, stock),
                    Math.max(0L, targetStock),
                    Math.max(0L, stockLimit),
                    Math.max(0.0d, drainRate),
                    Math.max(0.0d, refillRate),
                    stockMode,
                    minPriceMultiplier > 0.0d ? minPriceMultiplier : 0.25d,
                    maxPriceMultiplier > 0.0d ? maxPriceMultiplier : 4.0d,
                    spreadPercent >= 0.0d ? spreadPercent : 25.0d,
                    Math.max(0.0d, drainPercent),
                    Math.max(0L, drainMax),
                    Math.max(0.0d, restockPercent),
                    Math.max(0L, restockMax),
                    Math.max(0L, perPlayerDailySellLimit),
                    Math.max(0L, globalDailySellLimit)));
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
        Set<String> exportedOffers = new HashSet<>();
        if (definitions != null) {
            for (ItemDefinition definition : definitions) {
                if (definition == null || definition.name == null || definition.name.isBlank()) {
                    continue;
                }
                DefinitionExport[] resolvedDefinitions = resolveDefinitionExports(definition.name);
                if (resolvedDefinitions.length > 0) {
                    first = appendResolvedDefinitionOffers(json, first, exportedOffers, definition.name,
                            resolvedDefinitions);
                    continue;
                }
                int variations = Math.max(1, definition.variations);
                for (int variantIndex = 0; variantIndex < variations; variantIndex++) {
                    Variant variant = definition.getVariant(variantIndex);
                    if (!exportedOffers.add(offerKey(definition.name, variantIndex))) {
                        continue;
                    }
                    if (!first) {
                        json.append(",\n");
                    }
                    first = false;
                    appendOfferJson(json, definition.name, variantIndex, variant == null ? "" : variant.name,
                            "", "", -1, -1);
                }
            }
        }
        json.append("\n]\n");
        Files.writeString(exportOfferFile, json.toString(), StandardCharsets.UTF_8);
    }

    private static void appendOfferJson(StringBuilder json, String itemName, int itemVariant, String variantName,
            String sourceItemName, String definitionType, int definitionId, int objectId) {
        String id = itemName + "." + itemVariant;
        json.append("  {\n")
                .append("    \"id\": \"").append(escape(id)).append("\",\n")
                .append("    \"itemName\": \"").append(escape(itemName)).append("\",\n")
                .append("    \"itemVariant\": ").append(itemVariant).append(",\n")
                .append("    \"amount\": 1,\n")
                .append("    \"basePrice\": 100,\n")
                .append("    \"stock\": 100,\n")
                .append("    \"targetStock\": 100,\n")
                .append("    \"stockLimit\": 1000,\n")
                .append("    \"stockMode\": \"STATIC\",\n")
                .append("    \"minPriceMultiplier\": 0.25,\n")
                .append("    \"maxPriceMultiplier\": 4.0,\n")
                .append("    \"spreadPercent\": 25,\n")
                .append("    \"drainPercent\": 0,\n")
                .append("    \"drainMax\": 0,\n")
                .append("    \"restockPercent\": 0,\n")
                .append("    \"restockMax\": 0,\n")
                .append("    \"perPlayerDailySellLimit\": 0,\n")
                .append("    \"globalDailySellLimit\": 0,\n")
                .append("    \"isEnabled\": false");
        if (variantName != null && !variantName.isBlank()) {
            json.append(",\n    \"_variantName\": \"").append(escape(variantName)).append("\"");
        }
        if (sourceItemName != null && !sourceItemName.isBlank()) {
            json.append(",\n    \"_sourceItemName\": \"").append(escape(sourceItemName)).append("\"");
        }
        if (definitionType != null && !definitionType.isBlank()) {
            json.append(",\n    \"_definitionType\": \"").append(escape(definitionType)).append("\"");
        }
        if (definitionId >= 0) {
            json.append(",\n    \"_definitionId\": ").append(definitionId);
        }
        if (objectId >= 0) {
            json.append(",\n    \"_objectId\": ").append(objectId);
        }
        json.append("\n  }");
    }

    private static boolean isKnownOfferDefinition(String itemName) {
        return Definitions.getItemDefinition(itemName) != null
                || Definitions.getObjectDefinition(itemName) != null
                || Definitions.getConstructionDefinition(itemName) != null
                || Definitions.getClothingDefinition(itemName) != null
                || Definitions.getPlantDefinition(itemName) != null;
    }

    private static boolean hasRelatedItem(String relatedItem, String itemName) {
        return relatedItem != null && relatedItem.equalsIgnoreCase(itemName);
    }

    private static DefinitionExport[] resolveDefinitionExports(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return new DefinitionExport[0];
        }
        if (itemName.toLowerCase(Locale.ROOT).startsWith("objectkit")) {
            ObjectDefinition[] definitions = Definitions.getAllObjectDefinitions();
            List<DefinitionExport> result = new ArrayList<>();
            if (definitions != null) {
                for (ObjectDefinition definition : definitions) {
                    if (definition != null && definition.name != null && !definition.name.isBlank()
                            && hasRelatedItem(definition.relateditem, itemName)) {
                        result.add(DefinitionExport.object(definition));
                    }
                }
            }
            addObjectKitVariantDefinitions(result, itemName);
            return result.toArray(DefinitionExport[]::new);
        }
        if (itemName.equalsIgnoreCase("constructionitem")) {
            ConstructionDefinition[] definitions = Definitions.getAllConstructionDefinitions();
            Recipe[] recipes = Definitions.getAllRecipes();
            List<DefinitionExport> result = new ArrayList<>();
            if (definitions != null) {
                for (ConstructionDefinition definition : definitions) {
                    if (definition != null && definition.name != null && !definition.name.isBlank()
                            && hasRelatedItem(definition.relateditem, itemName)) {
                        result.add(DefinitionExport.construction(definition,
                                constructionItemVariants(definition, itemName, recipes)));
                    }
                }
            }
            return result.toArray(DefinitionExport[]::new);
        }
        if (itemName.equalsIgnoreCase("clothingitem")) {
            ClothingDefinition[] definitions = Definitions.getAllClothingDefinitions();
            List<DefinitionExport> result = new ArrayList<>();
            if (definitions != null) {
                for (ClothingDefinition definition : definitions) {
                    if (definition != null && definition.name != null && !definition.name.isBlank()
                            && hasRelatedItem(definition.relateditem, itemName)) {
                        result.add(DefinitionExport.clothing(definition));
                    }
                }
            }
            return result.toArray(DefinitionExport[]::new);
        }
        if (itemName.equalsIgnoreCase("plantitem")) {
            PlantDefinition[] definitions = Definitions.getAllPlantDefinitions();
            List<DefinitionExport> result = new ArrayList<>();
            if (definitions != null) {
                for (PlantDefinition definition : definitions) {
                    if (definition != null && definition.name != null && !definition.name.isBlank()
                            && referencesItem(definition, itemName)) {
                        result.add(DefinitionExport.plant(definition));
                    }
                }
            }
            return result.toArray(DefinitionExport[]::new);
        }
        return new DefinitionExport[0];
    }

    private static int[] constructionItemVariants(ConstructionDefinition definition, String itemName,
            Recipe[] recipes) {
        Set<Integer> variants = new HashSet<>();
        if (definition.supportedtextures != null) {
            for (int texture : definition.supportedtextures) {
                if (texture >= 0) {
                    variants.add(texture);
                }
            }
        }
        if (recipes != null) {
            for (Recipe recipe : recipes) {
                if (recipe != null && recipe.texture >= 0 && recipe.name != null
                        && recipe.name.equalsIgnoreCase(definition.name)
                        && recipe.itemDef != null && hasRelatedItem(recipe.itemDef.name, itemName)) {
                    variants.add(recipe.texture);
                }
            }
        }
        return variants.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private static void addObjectKitVariantDefinitions(List<DefinitionExport> result, String itemName) {
        Set<Integer> knownObjectIds = new HashSet<>();
        for (DefinitionExport export : result) {
            if (export.objectId() >= 0) {
                knownObjectIds.add(export.objectId());
            }
        }
        ItemDefinition itemDefinition = Definitions.getItemDefinition(itemName);
        if (itemDefinition == null) {
            return;
        }
        int variations = Math.max(1, itemDefinition.variations);
        for (int variantIndex = 0; variantIndex < variations; variantIndex++) {
            Variant variant = itemDefinition.getVariant(variantIndex);
            ObjectDefinition definition = variant == null || variant.name == null || variant.name.isBlank()
                    ? null
                    : Definitions.getObjectDefinition(variant.name);
            if (definition == null) {
                definition = Definitions.getObjectDefinition(variantIndex);
            }
            if (definition == null || definition.name == null || definition.name.isBlank()) {
                continue;
            }
            int objectId = Short.toUnsignedInt(definition.id);
            if (knownObjectIds.add(objectId)) {
                result.add(DefinitionExport.object(definition));
            }
        }
    }

    private static boolean referencesItem(PlantDefinition definition, String itemName) {
        return hasRelatedItem(definition.pickupitem, itemName)
                || hasRelatedItem(definition.harvestitem, itemName)
                || hasRelatedItem(definition.destroyitem, itemName)
                || hasRelatedItem(definition.sapling, itemName);
    }

    private static boolean appendResolvedDefinitionOffers(StringBuilder json, boolean first, Set<String> exportedOffers,
            String sourceItemName, DefinitionExport[] definitions) {
        for (DefinitionExport definition : definitions) {
            for (int itemVariant : definition.itemVariants()) {
                if (!exportedOffers.add(offerKey(definition.name(), itemVariant))) {
                    continue;
                }
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                appendOfferJson(json, definition.name(), itemVariant, definition.variantName(itemVariant),
                        sourceItemName, definition.definitionType(), definition.id(), definition.objectId());
            }
        }
        return first;
    }

    private static String offerKey(String itemName, int itemVariant) {
        return (itemName == null ? "" : itemName.toLowerCase(Locale.ROOT)) + "." + itemVariant;
    }

    private record DefinitionExport(String name, int variations, int[] explicitItemVariants, String definitionType, int id, int objectId,
            Object source) {
        static DefinitionExport object(ObjectDefinition definition) {
            int id = Short.toUnsignedInt(definition.id);
            return new DefinitionExport(definition.name, definition.variations, null, "object", id,
                    id, definition);
        }

        static DefinitionExport construction(ConstructionDefinition definition, int[] itemVariants) {
            return new DefinitionExport(definition.name, 1, itemVariants, "construction",
                    Byte.toUnsignedInt(definition.id), -1, definition);
        }

        static DefinitionExport clothing(ClothingDefinition definition) {
            return new DefinitionExport(definition.name, definition.variations, null, "clothing",
                    Short.toUnsignedInt(definition.id), -1, definition);
        }

        static DefinitionExport plant(PlantDefinition definition) {
            return new DefinitionExport(definition.name, 1, null, "plant", Short.toUnsignedInt(definition.id), -1,
                    definition);
        }

        int[] itemVariants() {
            if (explicitItemVariants != null && explicitItemVariants.length > 0) {
                return explicitItemVariants;
            }
            int variationCount = Math.max(1, variations);
            int[] result = new int[variationCount];
            for (int index = 0; index < variationCount; index++) {
                result[index] = index;
            }
            return result;
        }

        String variantName(int itemVariant) {
            if (source instanceof ObjectDefinition definition) {
                ObjectDefinition.Variant variant = definition.getVariant(itemVariant);
                return variant == null ? "" : variant.name;
            }
            return "";
        }
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
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return defaultValue;
    }

    static boolean enabledValue(Map<String, Object> object) {
        if (object.containsKey("isEnabled")) {
            return booleanValue(object, "isEnabled", false);
        }
        if (object.containsKey("enabled")) {
            return booleanValue(object, "enabled", false);
        }
        return booleanValue(object, "sellEnabled", false) || booleanValue(object, "buyEnabled", false);
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
