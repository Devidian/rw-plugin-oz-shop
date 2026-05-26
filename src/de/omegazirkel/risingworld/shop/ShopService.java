package de.omegazirkel.risingworld.shop;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.omegazirkel.risingworld.Shop;
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
        return registerPluginOffer(id, title, description, price, currencyIdentifier, icon, pluginIdentifier, callback,
                null);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String pluginIdentifier,
            ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        return registerPluginOffer(id, title, description, price, currencyIdentifier, icon, "", pluginIdentifier,
                pluginIdentifier, callback, priceResolver);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String category,
            String source,
            String pluginIdentifier,
            ShopPurchaseCallback callback) {
        return registerPluginOffer(id, title, description, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, callback, null);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String category,
            String source,
            String pluginIdentifier,
            ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
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
                safe(category), isBlank(source) ? normalizedPlugin : safe(source), normalizedPlugin, true, false,
                callback, priceResolver);
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

    public synchronized int unregisterPluginOffers(String pluginIdentifier) {
        String normalizedPlugin = normalizePlugin(pluginIdentifier);
        if (normalizedPlugin.isBlank()) {
            return 0;
        }
        int before = offers.size();
        offers.entrySet().removeIf(entry -> !entry.getValue().isSystemOffer()
                && entry.getValue().getPluginIdentifier().equals(normalizedPlugin));
        return before - offers.size();
    }

    public synchronized void replaceSystemOffers(List<ShopOffer> systemOffers) {
        offers.entrySet().removeIf(entry -> entry.getValue().isSystemOffer());
        for (ShopOffer offer : systemOffers) {
            String normalizedId = normalizeId(offer.getId());
            if (normalizedId.isBlank() || offers.containsKey(normalizedId)) {
                continue;
            }
            offers.put(normalizedId, new ShopOffer(normalizedId, offer.getTitle(), offer.getDescription(),
                    offer.getItemName(), offer.getItemTypeId(), offer.getItemVariant(), offer.getAmount(),
                    offer.getBasePrice(), offer.getBuyPrice(), offer.getSellPrice(), offer.getCurrencyIdentifier(),
                    offer.getIcon(), offer.getCategory(), offer.getSource(), SYSTEM_PLUGIN, offer.isBuyEnabled(),
                    offer.isSellEnabled(), true, offer.getCallback(), offer.getPriceResolver()));
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

    public synchronized List<ShopOffer> listPluginOffers() {
        return offers.values().stream()
                .filter(offer -> !offer.isSystemOffer())
                .sorted(Comparator.comparing(ShopOffer::getId))
                .toList();
    }

    public synchronized List<ShopOffer> listSystemOffers() {
        return offers.values().stream()
                .filter(ShopOffer::isSystemOffer)
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
        long price;
        try {
            price = offer.getPrice(player);
        } catch (RuntimeException ex) {
            Shop.logger().error("Shop price resolution failed for " + offer.getId() + ": " + ex.getMessage());
            return ShopPurchaseResult.failure(ShopErrorCode.PRICE_RESOLUTION_FAILED,
                    "Could not resolve offer price: " + ex.getMessage());
        }

        WalletBridge.WalletCallResult payment = WalletBridge.WalletCallResult.success("No payment required.");
        if (price > 0) {
            payment = offer.getCurrencyIdentifier().isBlank()
                    ? wallet.withdrawDefault(player.getDbID(), price, "Shop purchase: " + offer.getId(),
                            "OZ - Shop")
                    : wallet.withdraw(player.getDbID(), price, "Shop purchase: " + offer.getId(),
                            offer.getCurrencyIdentifier(), "OZ - Shop");
            if (!payment.success()) {
                Shop.logger().error(payment.toString());
                return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, payment.message());
            }
        }

        try {
            ShopPurchaseResult callbackResult = offer.getCallback().complete(player, offer);
            if (callbackResult == null) {
                return failAfterPayment(player, offer, price, "Purchase action returned no result after payment.");
            }
            if (!callbackResult.success && price > 0) {
                return failAfterPayment(player, offer, price, callbackResult.message);
            }
            return callbackResult;
        } catch (RuntimeException ex) {
            Shop.logger().error(ex.getMessage());
            return failAfterPayment(player, offer, price, "Purchase action failed after payment: " + ex.getMessage());
        }
    }

    private ShopPurchaseResult failAfterPayment(Player player, ShopOffer offer, long price, String failureMessage) {
        String message = isBlank(failureMessage) ? "Purchase action failed after payment." : failureMessage;
        if (price <= 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, message);
        }
        WalletBridge.WalletCallResult refund = refund(player, offer, price);
        if (!refund.success()) {
            Shop.logger().error("Shop refund failed for " + offer.getId() + ": " + refund.message());
            return ShopPurchaseResult.failure(ShopErrorCode.REFUND_FAILED,
                    message + " Refund failed: " + refund.message());
        }
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                message + " Payment was refunded.");
    }

    private WalletBridge.WalletCallResult refund(Player player, ShopOffer offer, long price) {
        if (price <= 0) {
            return WalletBridge.WalletCallResult.success("No refund required.");
        }
        if (offer.getCurrencyIdentifier().isBlank()) {
            return wallet.depositDefault(player.getDbID(), price, "Shop refund: " + offer.getId(), "OZ - Shop");
        }
        return wallet.deposit(player.getDbID(), price, "Shop refund: " + offer.getId(),
                offer.getCurrencyIdentifier(), "OZ - Shop");
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        Variant variant = definition == null ? null : definition.getVariant(itemVariant);
        String title = variant != null && variant.name != null && !variant.name.isBlank()
                ? variant.name
                : itemName + ":" + itemVariant;
        return new ShopOffer(normalizeId(id), title, "", itemName, definition.id, itemVariant, amount, basePrice,
                buyPrice, sellPrice, currencyIdentifier, "", "system", "OZ - Shop", SYSTEM_PLUGIN, buyEnabled,
                sellEnabled, true, (player, offer) -> {
                    Item item = player.getInventory().addItem(offer.getItemTypeId(), offer.getItemVariant(),
                            offer.getAmount());
                    if (item == null) {
                        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                                "Could not add item to inventory.");
                    }
                    player.getInventory().syncWithClient();
                    return ShopPurchaseResult.success("Purchase completed.", offer);
                }, null);
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
