package de.omegazirkel.risingworld.shop;

import java.util.Locale;

import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.definitions.Plants.PlantDefinition;
import net.risingworld.api.objects.Item;

public final class ShopItemNames {
    private ShopItemNames() {
    }

    public static String label(String itemName, int itemVariant, String fallbackTitle) {
        ObjectDefinition objectDefinition = objectDefinition(itemName, itemVariant);
        int labelVariant = objectDefinition == null ? itemVariant : objectVariant(itemName, itemVariant, objectDefinition);
        ItemDefinition itemDefinition = Definitions.getItemDefinition(itemName);
        if (objectDefinition == null && itemDefinition != null) {
            Variant variant = itemDefinition.getVariant(itemVariant);
            String itemDisplayName = itemDefinition.name == null || itemDefinition.name.isBlank()
                    ? itemName
                    : itemDefinition.name;
            if (variant != null) {
                if (!isDefaultVariantName(variant.name)) {
                    return derivedBaseName(itemDisplayName) + " " + derivedBaseName(variant.name);
                }
                return derivedBaseName(itemDisplayName);
            }
        }
        String baseName = objectDisplayName(itemName, itemVariant);
        if (baseName.isBlank() && objectDefinition == null) {
            baseName = objectVariantDisplayName(itemName, itemVariant);
        }
        if (baseName.isBlank()) {
            baseName = itemVariantDisplayName(itemName, itemVariant);
        }
        if (baseName.isBlank()) {
            baseName = directDefinitionDisplayName(itemName);
        }
        if (baseName.isBlank()) {
            baseName = fallbackTitle == null || fallbackTitle.isBlank() ? itemName : fallbackTitle;
        }
        return labelVariant == 0 ? derivedBaseName(baseName) : derivedBaseName(baseName) + "-" + labelVariant;
    }

    public static ObjectDefinition objectDefinition(String itemName, int itemVariant) {
        ObjectDefinition direct = Definitions.getObjectDefinition(itemName);
        if (direct != null) {
            return direct;
        }
        ItemDefinition itemDefinition = Definitions.getItemDefinition(itemName);
        if (itemDefinition != null) {
            Variant variant = itemDefinition.getVariant(itemVariant);
            if (variant != null && !isDefaultVariantName(variant.name)) {
                ObjectDefinition byVariantName = Definitions.getObjectDefinition(variant.name);
                if (byVariantName != null) {
                    return byVariantName;
                }
            }
        }
        return isObjectKit(itemName) ? Definitions.getObjectDefinition(itemVariant) : null;
    }

    public static int objectVariant(String itemName, int itemVariant, ObjectDefinition objectDefinition) {
        if (objectDefinition == null) {
            return itemVariant;
        }
        return Definitions.getObjectDefinition(itemName) != null ? itemVariant : 0;
    }

    /** Matches the inventory representation used for the resolved offer. */
    public static boolean matches(Item item, String itemName, int itemVariant, short itemTypeId) {
        if (item == null || !item.isValid()) return false;
        ObjectDefinition objectDefinition = objectDefinition(itemName, itemVariant);
        if (objectDefinition != null && item instanceof Item.ObjectItem objectItem) {
            String objectName = objectItem.getObjectName();
            return objectName != null && objectName.equalsIgnoreCase(objectDefinition.name)
                    && item.getVariant() == objectVariant(itemName, itemVariant, objectDefinition);
        }
        ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(itemName);
        if (constructionDefinition != null && item instanceof Item.ConstructionItem constructionItem) {
            String constructionName = constructionItem.getConstructionName();
            return constructionName != null && constructionName.equalsIgnoreCase(constructionDefinition.name)
                    && item.getVariant() == itemVariant;
        }
        ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(itemName);
        if (clothingDefinition != null && item instanceof Item.ClothingItem clothingItem) {
            String clothingName = clothingItem.getClothingName();
            return clothingName != null && clothingName.equalsIgnoreCase(clothingDefinition.name)
                    && item.getVariant() == itemVariant;
        }
        PlantDefinition plantDefinition = Definitions.getPlantDefinition(itemName);
        if (plantDefinition != null) {
            String resolvedName = item.getName();
            return resolvedName != null && resolvedName.equalsIgnoreCase(plantDefinition.name)
                    && item.getVariant() == itemVariant;
        }
        return item.getTypeID() == itemTypeId && item.getVariant() == itemVariant;
    }

    private static String objectVariantDisplayName(String itemName, int itemVariant) {
        ObjectDefinition definition = objectDefinition(itemName, itemVariant);
        if (definition == null) {
            return "";
        }
        ObjectDefinition.Variant variant = definition.getVariant(objectVariant(itemName, itemVariant, definition));
        if (variant == null || isDefaultVariantName(variant.name)) {
            return "";
        }
        return variant.name;
    }

    private static String objectDisplayName(String itemName, int itemVariant) {
        ObjectDefinition definition = objectDefinition(itemName, itemVariant);
        if (definition == null || definition.name == null || definition.name.isBlank()) {
            return "";
        }
        return definition.name;
    }

    private static String itemVariantDisplayName(String itemName, int itemVariant) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        if (definition == null) {
            return "";
        }
        Variant variant = definition.getVariant(itemVariant);
        if (variant == null || isDefaultVariantName(variant.name)) {
            return definition.name == null ? "" : definition.name;
        }
        return variant.name;
    }

    public static boolean isDefaultVariantName(String variantName) {
        return variantName == null || variantName.isBlank() || variantName.trim().equalsIgnoreCase("default");
    }

    private static String directDefinitionDisplayName(String itemName) {
        ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(itemName);
        if (constructionDefinition != null && constructionDefinition.name != null
                && !constructionDefinition.name.isBlank()) {
            return constructionDefinition.name;
        }
        ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(itemName);
        if (clothingDefinition != null && clothingDefinition.name != null && !clothingDefinition.name.isBlank()) {
            return clothingDefinition.name;
        }
        PlantDefinition plantDefinition = Definitions.getPlantDefinition(itemName);
        if (plantDefinition != null && plantDefinition.name != null && !plantDefinition.name.isBlank()) {
            return plantDefinition.name;
        }
        return "";
    }

    private static boolean isObjectKit(String itemName) {
        return itemName != null && itemName.toLowerCase(Locale.ROOT).startsWith("objectkit");
    }

    private static String derivedBaseName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "Item";
        }
        normalized = normalized.replace('_', ' ').replace('-', ' ');
        String[] parts = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.isEmpty() ? "Item" : result.toString();
    }
}
