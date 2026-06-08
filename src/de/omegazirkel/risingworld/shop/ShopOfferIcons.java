package de.omegazirkel.risingworld.shop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Objects.ObjectDefinition;

public final class ShopOfferIcons {
    private static final Map<String, TextureAsset> ICONS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private ShopOfferIcons() {
    }

    public static synchronized TextureAsset resolve(ShopOffer offer) {
        if (offer == null) {
            return fallbackIcon();
        }
        String key = cacheKey(offer);
        TextureAsset cached = ICONS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(key)) {
            return fallbackIcon();
        }

        TextureAsset asset = resolveOfferIcon(offer);
        if (asset == null) {
            MISSING.add(key);
            return fallbackIcon();
        }
        ICONS.put(key, asset);
        return asset;
    }

    public static PreloadResult preload(List<ShopOffer> offers) {
        long startedAt = System.nanoTime();
        int loaded = 0;
        int failed = 0;
        if (offers != null) {
            for (ShopOffer offer : offers) {
                if (offer == null || !offer.isSystemOffer() || !offer.isEnabled()) {
                    continue;
                }
                try {
                    if (resolve(offer) != null) {
                        loaded++;
                    } else {
                        failed++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
        }
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new PreloadResult(loaded, failed, durationMillis);
    }

    private static TextureAsset resolveOfferIcon(ShopOffer offer) {
        if (offer.isSystemOffer() && !offer.getItemName().isBlank()) {
            ObjectDefinition objectDefinition = ShopItemNames.objectDefinition(offer.getItemName(),
                    offer.getItemVariant());
            ObjectDefinition.Variant objectVariant = objectDefinition == null
                    ? null
                    : objectDefinition.getVariant(
                            ShopItemNames.objectVariant(offer.getItemName(), offer.getItemVariant(), objectDefinition));
            if (objectVariant != null) {
                TextureAsset asset = objectDefinition.getIcon(objectVariant.variant);
                if (asset != null) {
                    return asset;
                }
            }
            ItemDefinition definition = Definitions.getItemDefinition(offer.getItemName());
            ItemDefinition.Variant itemVariant = definition == null ? null : definition.getVariant(offer.getItemVariant());
            if (itemVariant != null) {
                TextureAsset asset = definition.getIcon(itemVariant.variant);
                if (asset != null) {
                    return asset;
                }
            }
        }
        return offer.getIcon().isBlank() ? null : AssetManager.getIcon(offer.getIcon());
    }

    private static TextureAsset fallbackIcon() {
        return AssetManager.getIcon("shop-icon");
    }

    private static String cacheKey(ShopOffer offer) {
        if (offer.isSystemOffer() && !offer.getItemName().isBlank()) {
            return "system:" + offer.getItemName().toLowerCase(Locale.ROOT) + ":" + offer.getItemVariant();
        }
        return "asset:" + offer.getIcon();
    }

    public record PreloadResult(int loaded, int failed, long durationMillis) {
    }
}
