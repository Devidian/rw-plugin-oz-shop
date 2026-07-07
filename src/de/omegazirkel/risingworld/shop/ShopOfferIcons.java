package de.omegazirkel.risingworld.shop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ToolsPlayerPreferences;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.objects.Player;

public final class ShopOfferIcons {
    private static final Map<String, TextureAsset> ICONS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private ShopOfferIcons() {
    }

    public static synchronized TextureAsset resolve(ShopOffer offer) {
        return resolve(null, offer);
    }

    public static synchronized TextureAsset resolve(Player player, ShopOffer offer) {
        if (offer == null) {
            return fallbackIcon(player);
        }
        String key = cacheKey(player, offer);
        TextureAsset cached = ICONS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(key)) {
            return fallbackIcon(player);
        }

        TextureAsset asset = resolveOfferIcon(player, offer);
        if (asset == null) {
            MISSING.add(key);
            return fallbackIcon(player);
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

    private static TextureAsset resolveOfferIcon(Player player, ShopOffer offer) {
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
        return offer.getIcon().isBlank() ? null : AssetManager.getIcon(player, offer.getIcon());
    }

    private static TextureAsset fallbackIcon(Player player) {
        return AssetManager.getIcon(player, "shop-icon");
    }

    private static String cacheKey(ShopOffer offer) {
        return cacheKey(null, offer);
    }

    private static String cacheKey(Player player, ShopOffer offer) {
        if (offer.isSystemOffer() && !offer.getItemName().isBlank()) {
            return "system:" + offer.getItemName().toLowerCase(Locale.ROOT) + ":" + offer.getItemVariant();
        }
        return "asset:" + AssetManager.normalizeStyle(ToolsPlayerPreferences.iconStyle(player)) + ":" + offer.getIcon();
    }

    public record PreloadResult(int loaded, int failed, long durationMillis) {
    }
}
