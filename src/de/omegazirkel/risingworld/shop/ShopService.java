package de.omegazirkel.risingworld.shop;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

public class ShopService {
    private static final String SYSTEM_PLUGIN = "system";

    private final Map<String, ShopOffer> offers = new LinkedHashMap<>();
    private final WalletBridge wallet;

    public ShopService(WalletBridge wallet) {
        this.wallet = wallet;
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String pluginIdentifier,
            ShopPurchaseCallback callback) {
        String normalizedId = normalizeId(id);
        String normalizedPlugin = normalizePlugin(pluginIdentifier);
        if (normalizedId.isBlank() || normalizedPlugin.isBlank() || isBlank(title) || price < 0 || callback == null) {
            return ShopOfferRegistrationResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                    "Offer id, title, plugin identifier, non-negative price, and callback are required.");
        }
        ShopOffer existing = offers.get(normalizedId);
        if (existing != null && !existing.getPluginIdentifier().equals(normalizedPlugin)) {
            return ShopOfferRegistrationResult.failure(ShopErrorCode.DUPLICATE_OFFER,
                    "Offer id is already registered by another plugin.");
        }
        ShopOffer offer = new ShopOffer(normalizedId, title.trim(), safe(description), price, currencyIdentifier, icon,
                normalizedPlugin, true, false, callback);
        offers.put(normalizedId, offer);
        return ShopOfferRegistrationResult.success("Offer registered.", offer);
    }

    public synchronized ShopOfferRegistrationResult unregisterOffer(String id, String pluginIdentifier) {
        String normalizedId = normalizeId(id);
        String normalizedPlugin = normalizePlugin(pluginIdentifier);
        ShopOffer existing = offers.get(normalizedId);
        if (existing == null) {
            return ShopOfferRegistrationResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Offer is not registered.");
        }
        if (!existing.getPluginIdentifier().equals(normalizedPlugin)) {
            return ShopOfferRegistrationResult.failure(ShopErrorCode.OWNER_MISMATCH,
                    "Only the registering plugin may unregister the offer.");
        }
        offers.remove(normalizedId);
        return ShopOfferRegistrationResult.success("Offer unregistered.", existing);
    }

    public synchronized void replaceSystemOffers(List<ShopOffer> systemOffers) {
        offers.entrySet().removeIf(entry -> entry.getValue().isSystemOffer());
        for (ShopOffer offer : systemOffers) {
            String normalizedId = normalizeId(offer.getId());
            if (normalizedId.isBlank() || offers.containsKey(normalizedId)) {
                continue;
            }
            offers.put(normalizedId, new ShopOffer(normalizedId, offer.getTitle(), offer.getDescription(),
                    offer.getPrice(), offer.getCurrencyIdentifier(), offer.getIcon(), SYSTEM_PLUGIN, offer.isEnabled(),
                    true, offer.getCallback()));
        }
    }

    public synchronized Optional<ShopOffer> findOffer(String offerId) {
        return Optional.ofNullable(offers.get(normalizeId(offerId)));
    }

    public synchronized List<ShopOffer> listOffers() {
        return offers.values().stream()
                .sorted(Comparator.comparing(ShopOffer::getId))
                .toList();
    }

    public synchronized void clear() {
        offers.clear();
    }

    public boolean walletAvailable() {
        return wallet.isAvailable();
    }

    public ShopPurchaseResult purchase(Player player, String offerId) {
        if (player == null || player.getDbID() <= 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "A valid player is required.");
        }

        ShopOffer offer;
        synchronized (this) {
            offer = offers.get(normalizeId(offerId));
        }
        if (offer == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Offer not found.");
        }
        if (!offer.isEnabled()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, "Offer is disabled.");
        }
        if (offer.getCallback() == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_MISSING,
                    "Offer has no purchase action registered.");
        }
        if (!wallet.isAvailable()) {
            return ShopPurchaseResult.failure(ShopErrorCode.WALLET_UNAVAILABLE, "OZ - Wallet is not available.");
        }

        WalletBridge.WalletCallResult payment = WalletBridge.WalletCallResult.success("No payment required.");
        if (offer.getPrice() > 0) {
            payment = offer.getCurrencyIdentifier().isBlank()
                    ? wallet.withdrawDefault(player.getDbID(), offer.getPrice(), "Shop purchase: " + offer.getId(),
                            "OZ - Shop")
                    : wallet.withdraw(player.getDbID(), offer.getPrice(), "Shop purchase: " + offer.getId(),
                            offer.getCurrencyIdentifier(), "OZ - Shop");
            if (!payment.success()) {
                return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, payment.message());
            }
        }

        try {
            ShopPurchaseResult callbackResult = offer.getCallback().complete(player, offer);
            if (callbackResult == null) {
                refund(player, offer);
                return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                        "Purchase action returned no result after payment.");
            }
            if (!callbackResult.success && offer.getPrice() > 0) {
                refund(player, offer);
            }
            return callbackResult;
        } catch (RuntimeException ex) {
            refund(player, offer);
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                    "Purchase action failed after payment: " + ex.getMessage());
        }
    }

    private void refund(Player player, ShopOffer offer) {
        if (offer.getPrice() <= 0) {
            return;
        }
        if (offer.getCurrencyIdentifier().isBlank()) {
            wallet.depositDefault(player.getDbID(), offer.getPrice(), "Shop refund: " + offer.getId(), "OZ - Shop");
        } else {
            wallet.deposit(player.getDbID(), offer.getPrice(), "Shop refund: " + offer.getId(),
                    offer.getCurrencyIdentifier(), "OZ - Shop");
        }
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, long price, String currencyIdentifier,
            boolean enabled) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        Variant variant = definition == null ? null : definition.getVariant(itemVariant);
        String title = variant != null && variant.name != null && !variant.name.isBlank()
                ? variant.name
                : itemName + ":" + itemVariant;
        return new ShopOffer(normalizeId(id), title, "", itemName, itemVariant, price, currencyIdentifier, "",
                SYSTEM_PLUGIN, enabled, true, (player, offer) -> {
                    Item item = player.getInventory().addItem(offer.getItemName(), offer.getItemVariant(), 1);
                    if (item == null) {
                        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                                "Could not add item to inventory.");
                    }
                    player.getInventory().syncWithClient();
                    return ShopPurchaseResult.success("Purchase completed.", offer);
                });
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizePlugin(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
