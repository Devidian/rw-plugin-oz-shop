package de.omegazirkel.risingworld.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.Modifier;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.definitions.Plants.PlantDefinition;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Inventory.SlotType;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

public class ShopService {
    private static final String SYSTEM_PLUGIN = "system";
    private static final SlotType[] ITEM_ADD_SLOT_TYPES = { SlotType.Quickslot, SlotType.Inventory };

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
                pluginIdentifier, callback, priceResolver, null);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id, String title, String description, long price, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver, ShopOfferLocalization localization) {
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
        offer.setLocalization(localization);
        offers.put(normalizedId, offer);
        return ShopOfferRegistrationResult.success("Offer registered.", offer);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id, String title, String description, long price, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, ShopPurchaseCallback callback) {
        return registerPluginOffer(id, title, description, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, callback, null, null);
    }

    public synchronized ShopOfferRegistrationResult registerPluginOffer(
            String id, String title, String description, long price, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        return registerPluginOffer(id, title, description, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, callback, priceResolver, null);
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
                    offer.getIcon(), offer.getCategory(), offer.getSource(), SYSTEM_PLUGIN, offer.isEnabled(),
                    false, true, offer.getDefaultStock(), offer.getDefaultTargetStock(),
                    offer.getDefaultStockLimit(), offer.getDefaultDrainRate(), offer.getDefaultRefillRate(),
                    offer.getStockMode(), offer.getMinPriceMultiplier(), offer.getMaxPriceMultiplier(),
                    offer.getSpreadPercent(), offer.getDrainPercent(), offer.getDrainMax(), offer.getRestockPercent(),
                    offer.getRestockMax(), offer.getPerPlayerDailySellLimit(), offer.getGlobalDailySellLimit(),
                    offer.getCallback(), offer.getPriceResolver()));
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
        return purchase(player, offer);
    }

    public ShopPurchaseResult purchase(Player player, ShopOffer offer) {
        return purchase(player, offer, 1);
    }

    public ShopPurchaseResult purchase(Player player, ShopOffer offer, int quantity) {
        return purchase(player, offer, quantity, "");
    }

    /** Executes a system-item purchase whose payment is credited to one Wallet system account. */
    public ShopPurchaseResult purchaseFromSystemAccount(Player player, ShopOffer offer, int quantity,
            String payeeSystemAccountId) {
        return purchase(player, offer, quantity, payeeSystemAccountId);
    }

    private ShopPurchaseResult purchase(Player player, ShopOffer offer, int quantity, String payeeSystemAccountId) {
        if (player == null || player.getDbID() <= 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "A valid player is required.");
        }
        if (offer == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Offer not found.");
        }
        int effectiveQuantity = Math.max(1, quantity);
        if (effectiveQuantity > 1 && !offer.isSystemOffer()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                    "Quantity purchases are only supported for system-shop offers.");
        }
        ShopOffer effectiveOffer = effectiveQuantity == 1 ? offer : quantityOffer(offer, effectiveQuantity);
        if (!effectiveOffer.isEnabled()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, "Offer is disabled.");
        }
        if (effectiveOffer.getCallback() == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_MISSING,
                    "Offer has no purchase action registered.");
        }
        if (!wallet.isAvailable()) {
            return ShopPurchaseResult.failure(ShopErrorCode.WALLET_UNAVAILABLE, "OZ - Wallet is not available.");
        }
        long price;
        try {
            price = effectiveOffer.getPrice(player);
        } catch (RuntimeException ex) {
            Shop.logger().error("Shop price resolution failed for " + effectiveOffer.getId() + ": " + ex.getMessage());
            return ShopPurchaseResult.failure(ShopErrorCode.PRICE_RESOLUTION_FAILED,
                    "Could not resolve offer price: " + ex.getMessage());
        }
        if (effectiveOffer.isSystemOffer() && !canAddSystemOfferItem(player, effectiveOffer)) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVENTORY_FULL, "Your inventory is full.");
        }

        PaymentReceipt payment = PaymentReceipt.none();
        if (price > 0) {
            payment = charge(player, effectiveOffer, price, payeeSystemAccountId);
            if (!payment.result().success()) {
                Shop.logger().error(payment.result().toString());
                return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, payment.result().message());
            }
        }

        try {
            ShopPurchaseResult callbackResult = effectiveOffer.getCallback().complete(player, effectiveOffer);
            if (callbackResult == null) {
                return failAfterPayment(player, effectiveOffer, price, payment,
                        "Purchase action returned no result after payment.");
            }
            if (!callbackResult.success && price > 0) {
                return failAfterPayment(player, effectiveOffer, price, payment, callbackResult.message);
            }
            return callbackResult;
        } catch (RuntimeException ex) {
            Shop.logger().error(ex.getMessage());
            return failAfterPayment(player, effectiveOffer, price, payment,
                    "Purchase action failed after payment: " + ex.getMessage());
        }
    }

    public ShopPurchaseResult sell(Player player, ShopOffer offer, int quantity) {
        return sell(player, offer, quantity, "");
    }

    /** Executes a system-item sale paid from one Wallet system account. */
    public ShopPurchaseResult sellToSystemAccount(Player player, ShopOffer offer, int quantity,
            String payerSystemAccountId) {
        return sell(player, offer, quantity, payerSystemAccountId);
    }

    private ShopPurchaseResult sell(Player player, ShopOffer offer, int quantity, String payerSystemAccountId) {
        if (player == null || player.getDbID() <= 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "A valid player is required.");
        }
        if (offer == null || !offer.isSystemOffer()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "System offer not found.");
        }
        int effectiveQuantity = Math.max(1, quantity);
        ShopOffer effectiveOffer = effectiveQuantity == 1 ? offer : quantityOffer(offer, effectiveQuantity);
        if (!effectiveOffer.canPlayerSellToSystem()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, "Shop buying is disabled for this offer.");
        }
        if (!wallet.isAvailable()) {
            return ShopPurchaseResult.failure(ShopErrorCode.WALLET_UNAVAILABLE, "OZ - Wallet is not available.");
        }
        SellQuote quote = quoteSell(player, effectiveOffer);
        if (!quote.sellable()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, quote.message());
        }
        long payout = quote.payout();
        if (payout < 0L) {
            return ShopPurchaseResult.failure(ShopErrorCode.PRICE_RESOLUTION_FAILED,
                    "Could not resolve offer payout.");
        }
        ShopPurchaseResult removed = removeItems(player, effectiveOffer, quote);
        if (!removed.success) {
            return removed;
        }
        String currency = effectiveOffer.getCurrencyIdentifier().isBlank()
                ? wallet.defaultCurrencyIdentifier() : effectiveOffer.getCurrencyIdentifier();
        WalletBridge.WalletCallResult deposit;
        if (payerSystemAccountId != null && !payerSystemAccountId.isBlank() && !currency.isBlank()) {
            WalletBridge.WalletTransferCallResult transfer = wallet.transferSystemToPlayerIdempotent(
                    payerSystemAccountId, player.getDbID(), payout, "Shop sale: " + effectiveOffer.getId(), currency,
                    "OZ - Shop", "shop:sale:" + payerSystemAccountId + ":" + UUID.randomUUID());
            deposit = new WalletBridge.WalletCallResult(transfer.success(), transfer.message());
        } else {
            deposit = effectiveOffer.getCurrencyIdentifier().isBlank()
                    ? wallet.depositDefault(player.getDbID(), payout, "Shop sale: " + effectiveOffer.getId(),
                            "OZ - Shop")
                    : wallet.deposit(player.getDbID(), payout, "Shop sale: " + effectiveOffer.getId(),
                            effectiveOffer.getCurrencyIdentifier(), "OZ - Shop");
        }
        if (!deposit.success()) {
            ShopPurchaseResult returned = restoreRemovedItems(player, effectiveOffer, quote);
            if (!returned.success) {
                Shop.logger().error("Shop sale rollback failed for " + effectiveOffer.getId()
                        + ": could not return removed items.");
            }
            return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, deposit.message());
        }
        return ShopPurchaseResult.success("Sale completed.", quote.payoutOffer(effectiveOffer));
    }

    /** Plans the exact sellable inventory entries before inventory or Wallet mutation. */
    public SellQuote quoteSell(Player player, ShopOffer offer) {
        if (player == null || player.getInventory() == null || offer == null) return SellQuote.invalid("Inventory is unavailable.");
        int remaining = offer.getAmount();
        double unitPayout = Math.max(0L, offer.getBuyPrice()) / (double) Math.max(1, offer.getAmount());
        List<SellSelection> selections = new ArrayList<>();
        double payout = 0.0d;
        for (SlotType slotType : SlotType.values()) {
            for (int slot = 0; slot < player.getInventory().getSlotCount(slotType) && remaining > 0; slot++) {
                Item item = player.getInventory().getItem(slot, slotType);
                if (!matchesSystemOfferItem(item, offer)) continue;
                int amount = Math.min(remaining, item.getStack());
                int maxDurability = maxDurability(item);
                if (maxDurability > 0 && item.getDurability() <= 0) continue;
                Modifier modifier = item.getModifier();
                double normalPayout = conditionAdjustedPayoutExact(unitPayout, item.getDurability(), maxDurability,
                        Modifier.Normal);
                double itemPayout = conditionAdjustedPayoutExact(unitPayout, item.getDurability(), maxDurability,
                        modifier);
                selections.add(new SellSelection(slot, slotType, item.getStack(), amount, snapshot(item),
                        maxDurability, modifier == null ? "Normal" : modifier.name(),
                        modifierPayoutMultiplier(modifier), normalPayout * amount, floorPayout(normalPayout * amount),
                        floorPayout(itemPayout * amount)));
                payout += itemPayout * amount;
                remaining -= amount;
            }
        }
        if (selections.isEmpty()) return SellQuote.invalid("No sellable item with remaining durability is available.");
        return new SellQuote(selections, floorPayout(payout), offer.getAmount() - remaining,
                remaining == 0 ? "" : "Only items with remaining durability can be sold.");
    }

    private ShopPurchaseResult failAfterPayment(Player player, ShopOffer offer, long price, PaymentReceipt payment,
            String failureMessage) {
        String message = isBlank(failureMessage) ? "Purchase action failed after payment." : failureMessage;
        if (price <= 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, message);
        }
        WalletBridge.WalletCallResult refund = refund(player, offer, price, payment);
        if (!refund.success()) {
            Shop.logger().error("Shop refund failed for " + offer.getId() + ": " + refund.message());
            return ShopPurchaseResult.failure(ShopErrorCode.REFUND_FAILED,
                    message + " Refund failed: " + refund.message());
        }
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                message + " Payment was refunded.");
    }

    private PaymentReceipt charge(Player player, ShopOffer offer, long price, String payeeSystemAccountId) {
        String reason = "Shop purchase: " + offer.getId();
        String currency = offer.getCurrencyIdentifier().isBlank()
                ? wallet.defaultCurrencyIdentifier() : offer.getCurrencyIdentifier();
        if (wallet.hasSystemAccountApi() && !currency.isBlank()) {
            String correlation = "shop:purchase:" + player.getDbID() + ":" + UUID.randomUUID();
            WalletBridge.WalletTransferCallResult result = payeeSystemAccountId == null || payeeSystemAccountId.isBlank()
                    ? wallet.transferPlayerToWorldIdempotent(player.getDbID(), price, reason, currency, "OZ - Shop",
                            correlation)
                    : wallet.transferPlayerToSystemIdempotent(player.getDbID(), payeeSystemAccountId, price, reason,
                            currency, "OZ - Shop", correlation);
            return new PaymentReceipt(new WalletBridge.WalletCallResult(result.success(), result.message()),
                    correlation, true);
        }
        WalletBridge.WalletCallResult result = offer.getCurrencyIdentifier().isBlank()
                ? wallet.withdrawDefault(player.getDbID(), price, reason, "OZ - Shop")
                : wallet.withdraw(player.getDbID(), price, reason, offer.getCurrencyIdentifier(), "OZ - Shop");
        return new PaymentReceipt(result, "", false);
    }

    private WalletBridge.WalletCallResult refund(Player player, ShopOffer offer, long price, PaymentReceipt payment) {
        if (price <= 0) {
            return WalletBridge.WalletCallResult.success("No refund required.");
        }
        if (payment.routedToWorld()) {
            WalletBridge.WalletTransferCallResult reversal = wallet.reverseAccountTransferIdempotent(
                    payment.correlationId(), payment.correlationId() + ":refund", "Shop refund: " + offer.getId(),
                    "OZ - Shop");
            return new WalletBridge.WalletCallResult(reversal.success(), reversal.message());
        }
        if (offer.getCurrencyIdentifier().isBlank()) {
            return wallet.depositDefault(player.getDbID(), price, "Shop refund: " + offer.getId(), "OZ - Shop");
        }
        return wallet.deposit(player.getDbID(), price, "Shop refund: " + offer.getId(),
                offer.getCurrencyIdentifier(), "OZ - Shop");
    }

    private record PaymentReceipt(WalletBridge.WalletCallResult result, String correlationId,
            boolean routedToWorld) {
        private static PaymentReceipt none() {
            return new PaymentReceipt(WalletBridge.WalletCallResult.success("No payment required."), "", false);
        }
    }

    private ShopPurchaseResult removeItems(Player player, ShopOffer offer, SellQuote quote) {
        Inventory inventory = player.getInventory();
        List<SellSelection> removed = new ArrayList<>();
        for (SellSelection selection : quote.selections()) {
            Item item = inventory.getItem(selection.slot(), selection.slotType());
            if (!matchesSystemOfferItem(item, offer) || !sameState(item, selection.state())
                    || item.getStack() < selection.amount()
                    || !inventory.removeItem(selection.slot(), selection.slotType(), selection.amount())) {
                restoreSelections(inventory, offer, removed);
                return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, "Could not remove item from inventory.");
            }
            removed.add(selection);
        }
        inventory.syncWithClient();
        return ShopPurchaseResult.success("Items removed.", offer);
    }

    private ShopPurchaseResult restoreRemovedItems(Player player, ShopOffer offer, SellQuote quote) {
        return restoreSelections(player.getInventory(), offer, quote.selections())
                ? ShopPurchaseResult.success("Items restored.", offer)
                : ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, "Could not restore sold items.");
    }

    private static boolean restoreSelections(Inventory inventory, ShopOffer offer, List<SellSelection> selections) {
        for (int i = selections.size() - 1; i >= 0; i--) {
            SellSelection selection = selections.get(i);
            Item current = inventory.getItem(selection.slot(), selection.slotType());
            if (current != null && current.isValid() && sameState(current, selection.state())) {
                current.setStack(selection.originalStack());
                continue;
            }
            Item restored = addSystemOfferItemToSlot(inventory, offer, selection.amount(), selection.slot(), selection.slotType());
            if (restored == null || !restored.isValid()) return false;
            applyState(restored, selection.state());
        }
        inventory.syncWithClient();
        return true;
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                buyEnabled || sellEnabled, 0L, 0.0d, 0.0d);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean enabled) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                enabled, 0L, 0.0d, 0.0d);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled,
            long defaultStock, double defaultDrainRate, double defaultRefillRate) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                buyEnabled || sellEnabled, defaultStock, 0L, 0L, defaultDrainRate, defaultRefillRate);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean enabled,
            long defaultStock, double defaultDrainRate, double defaultRefillRate) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                enabled, defaultStock, 0L, 0L, defaultDrainRate, defaultRefillRate);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                buyEnabled || sellEnabled, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, ShopStockMode.STATIC, 0.25d, 4.0d, 25.0d);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean enabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                enabled, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, ShopStockMode.STATIC, 0.25d, 4.0d, 25.0d);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier, double maxPriceMultiplier,
            double spreadPercent) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                buyEnabled || sellEnabled, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, stockMode, minPriceMultiplier, maxPriceMultiplier, spreadPercent, 0.0d, 0L, 0.0d,
                0L, 0L, 0L);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean enabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier, double maxPriceMultiplier,
            double spreadPercent) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                enabled, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, stockMode, minPriceMultiplier, maxPriceMultiplier, spreadPercent, 0.0d, 0L, 0.0d,
                0L, 0L, 0L);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean buyEnabled, boolean sellEnabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier, double maxPriceMultiplier,
            double spreadPercent, double drainPercent, long drainMax, double restockPercent, long restockMax,
            long perPlayerDailySellLimit, long globalDailySellLimit) {
        return systemItemOffer(id, itemName, itemVariant, amount, basePrice, buyPrice, sellPrice, currencyIdentifier,
                buyEnabled || sellEnabled, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, stockMode, minPriceMultiplier, maxPriceMultiplier, spreadPercent, drainPercent,
                drainMax, restockPercent, restockMax, perPlayerDailySellLimit, globalDailySellLimit);
    }

    static ShopOffer systemItemOffer(String id, String itemName, int itemVariant, int amount, double basePrice,
            long buyPrice, long sellPrice, String currencyIdentifier, boolean enabled,
            long defaultStock, long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier, double maxPriceMultiplier,
            double spreadPercent, double drainPercent, long drainMax, double restockPercent, long restockMax,
            long perPlayerDailySellLimit, long globalDailySellLimit) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        ObjectDefinition objectDefinition = ShopItemNames.objectDefinition(itemName, itemVariant);
        Variant variant = definition == null ? null : definition.getVariant(itemVariant);
        String title;
        if (objectDefinition != null || definition == null) {
            title = ShopItemNames.label(itemName, itemVariant, "");
        } else if (variant != null && !ShopItemNames.isDefaultVariantName(variant.name)) {
            title = ShopItemNames.label(itemName, itemVariant, "");
        } else if (definition.name != null && !definition.name.isBlank()) {
            title = definition.name;
        } else {
            title = fallbackItemTitle(itemName, itemVariant);
        }
        short itemTypeId = definition == null ? 0 : definition.id;
        return new ShopOffer(normalizeId(id), title, "", itemName, itemTypeId, itemVariant, amount, basePrice,
                buyPrice, sellPrice, currencyIdentifier, "", "system", "OZ - Shop", SYSTEM_PLUGIN, enabled,
                false, true, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, stockMode, minPriceMultiplier, maxPriceMultiplier, spreadPercent, drainPercent,
                drainMax, restockPercent, restockMax, perPlayerDailySellLimit, globalDailySellLimit, (player, offer) -> {
                    return addSystemOfferItem(player, offer);
                }, null);
    }

    private static ShopPurchaseResult addSystemOfferItem(Player player, ShopOffer offer) {
        ShopPurchaseResult directResult = addSystemOfferItemToInventory(player, offer);
        if (directResult.success) {
            player.getInventory().syncWithClient();
            return directResult;
        }
        return directResult;
    }

    private static ShopPurchaseResult addSystemOfferItemToInventory(Player player, ShopOffer offer) {
        Inventory inventory = player.getInventory();
        int remaining = offer.getAmount();
        List<StackChange> stackChanges = new ArrayList<>();
        List<CreatedSlot> createdSlots = new ArrayList<>();

        for (SlotType slotType : ITEM_ADD_SLOT_TYPES) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (!matchesSystemOfferItem(item, offer)) {
                    continue;
                }
                int stack = Math.max(0, item.getStack());
                int maxStackSize = maxStackSize(item, offer);
                int free = Math.max(0, maxStackSize - stack);
                if (free <= 0) {
                    continue;
                }
                int add = Math.min(remaining, free);
                stackChanges.add(new StackChange(item, stack));
                item.setStack(stack + add);
                remaining -= add;
                if (remaining == 0) {
                    return ShopPurchaseResult.success("Purchase completed.", offer);
                }
            }
        }

        int emptySlotMax = maxStackSize(offer);
        for (SlotType slotType : ITEM_ADD_SLOT_TYPES) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (item != null && item.isValid()) {
                    continue;
                }
                int add = Math.min(remaining, emptySlotMax);
                Item created = addSystemOfferItemToSlot(inventory, offer, add, slot, slotType);
                if (created == null || !created.isValid()) {
                    rollbackInventoryAdd(inventory, stackChanges, createdSlots);
                    return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                            "Could not add item to inventory.");
                }
                createdSlots.add(new CreatedSlot(slot, slotType));
                remaining -= add;
                if (remaining == 0) {
                    return ShopPurchaseResult.success("Purchase completed.", offer);
                }
            }
        }

        rollbackInventoryAdd(inventory, stackChanges, createdSlots);
        return ShopPurchaseResult.failure(ShopErrorCode.INVENTORY_FULL, "Your inventory is full.");
    }

    private static Item addSystemOfferItemToSlot(Inventory inventory, ShopOffer offer, int amount, int slot,
            SlotType slotType) {
        ObjectDefinition objectDefinition = objectDefinition(offer);
        int objectVariant = objectVariant(offer, objectDefinition);
        if (objectDefinition != null) {
            return inventory.addObjectItemToSlot(objectDefinition.id, objectVariant, amount, slot, slotType);
        }
        ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(offer.getItemName());
        if (constructionDefinition != null) {
            return inventory.addConstructionItemToSlot(constructionDefinition.id, offer.getItemVariant(), 0, amount,
                    slot, slotType);
        }
        ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(offer.getItemName());
        if (clothingDefinition != null) {
            return inventory.addClothingItemToSlot(clothingDefinition.id, offer.getItemVariant(), 0, amount, 0L, slot,
                    slotType);
        }
        PlantDefinition plantDefinition = Definitions.getPlantDefinition(offer.getItemName());
        if (plantDefinition != null) {
            ItemDefinition definition = Definitions.getItemDefinition(offer.getItemName());
            if (definition == null) {
                return null;
            }
            return inventory.addItemToSlot(definition.id, offer.getItemVariant(), amount, slot, slotType);
        }
        return inventory.addItemToSlot(offer.getItemTypeId(), offer.getItemVariant(), amount, slot, slotType);
    }

    private static boolean canAddSystemOfferItem(Player player, ShopOffer offer) {
        Inventory inventory = player.getInventory();
        int remaining = offer.getAmount();
        for (SlotType slotType : ITEM_ADD_SLOT_TYPES) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (!matchesSystemOfferItem(item, offer)) {
                    continue;
                }
                remaining -= Math.max(0, maxStackSize(item, offer) - Math.max(0, item.getStack()));
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        int emptySlotMax = maxStackSize(offer);
        for (SlotType slotType : ITEM_ADD_SLOT_TYPES) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (item == null || !item.isValid()) {
                    remaining -= emptySlotMax;
                    if (remaining <= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int maxStackSize(Item item, ShopOffer offer) {
        int itemMax = item == null ? 0 : item.getMaxStackSize();
        return itemMax > 0 ? itemMax : maxStackSize(offer);
    }

    private static int maxStackSize(ShopOffer offer) {
        ItemDefinition definition = Definitions.getItemDefinition(offer.getItemName());
        if (definition == null && offer.getItemTypeId() > 0) {
            definition = Definitions.getItemDefinition(offer.getItemTypeId());
        }
        if (definition != null && definition.stacksize > 0) {
            return definition.stacksize;
        }
        return Math.max(1, offer.getAmount());
    }

    static int maxDurability(Item item) {
        ItemDefinition definition = item == null ? null : item.getDefinition();
        if (definition == null && item != null) definition = Definitions.getItemDefinition(item.getTypeID());
        return definition == null ? 0 : Math.max(0, definition.durability);
    }

    static long durabilityAdjustedPayout(long unitPayout, int durability, int maxDurability) {
        return conditionAdjustedPayout(unitPayout, durability, maxDurability, Modifier.Normal);
    }

    static long conditionAdjustedPayout(long unitPayout, int durability, int maxDurability, Modifier modifier) {
        return floorPayout(conditionAdjustedPayoutExact(unitPayout, durability, maxDurability, modifier));
    }

    static double conditionAdjustedPayoutExact(double unitPayout, int durability, int maxDurability, Modifier modifier) {
        if (maxDurability <= 0) return Math.max(0L, unitPayout);
        if (durability <= 0) return 0L;
        return Math.max(0.0d, unitPayout)
                * Math.min(1.0d, durability / (double) maxDurability)
                * modifierPayoutMultiplier(modifier);
    }

    private static long floorPayout(double payout) {
        if (!Double.isFinite(payout) || payout >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return payout <= 0.0d ? 0L : (long) Math.floor(payout + 1.0E-9d);
    }

    /** Applies the same aggregate rounding as the actual player payout. */
    static long traderPayoutCap(long payout, double normalPayout) {
        return Math.min(Math.max(0L, payout), floorPayout(normalPayout));
    }

    static double modifierPayoutMultiplier(Modifier modifier) {
        Modifier effective = modifier == null ? Modifier.Normal : modifier;
        if (effective == Modifier.Normal) return 1.0d;
        if (effective == Modifier.Broken) return 0.1d;
        int firstBetter = Modifier.Nice.ordinal();
        int godly = Modifier.Godly.ordinal();
        double multiplier = effective.ordinal() < firstBetter
                ? 0.1d + 0.9d * (effective.ordinal() - Modifier.Broken.ordinal())
                        / (firstBetter - Modifier.Broken.ordinal() - 1)
                : 1.1d + 8.9d * (effective.ordinal() - firstBetter) / (double) (godly - firstBetter);
        return Math.max(0.1d, Math.min(10.0d, multiplier));
    }

    private static ItemState snapshot(Item item) {
        return new ItemState(item.getDurability(), item.getStatus(), item.getModifier() == null ? "" : item.getModifier().name());
    }

    private static boolean sameState(Item item, ItemState state) {
        String modifier = item.getModifier() == null ? "" : item.getModifier().name();
        return item.getDurability() == state.durability() && item.getStatus() == state.status()
                && modifier.equals(state.modifier());
    }

    private static void applyState(Item item, ItemState state) {
        item.setDurability(state.durability());
        item.setStatus(state.status());
        try { item.setModifier(state.modifier().isBlank() ? null : Modifier.valueOf(state.modifier())); }
        catch (IllegalArgumentException ignored) { item.setModifier(null); }
    }

    private static void rollbackInventoryAdd(Inventory inventory, List<StackChange> stackChanges,
            List<CreatedSlot> createdSlots) {
        for (CreatedSlot createdSlot : createdSlots) {
            inventory.removeItem(createdSlot.slot, createdSlot.slotType);
        }
        for (int i = stackChanges.size() - 1; i >= 0; i--) {
            StackChange change = stackChanges.get(i);
            if (change.item != null && change.item.isValid()) {
                change.item.setStack(change.oldStack);
            }
        }
        inventory.syncWithClient();
    }

    private static boolean matchesSystemOfferItem(Item item, ShopOffer offer) {
        return offer != null && ShopItemNames.matches(item, offer.getItemName(), offer.getItemVariant(),
                offer.getItemTypeId());
    }

    private static ObjectDefinition objectDefinition(ShopOffer offer) {
        return ShopItemNames.objectDefinition(offer.getItemName(), offer.getItemVariant());
    }

    private static int objectVariant(ShopOffer offer, ObjectDefinition objectDefinition) {
        return ShopItemNames.objectVariant(offer.getItemName(), offer.getItemVariant(), objectDefinition);
    }

    private static String fallbackItemTitle(String itemName, int itemVariant) {
        return itemVariant == 0 ? itemName : itemName + ":" + itemVariant;
    }

    private static ShopOffer quantityOffer(ShopOffer offer, int quantity) {
        int amount = offer.getAmount() * quantity;
        return new ShopOffer(offer.getId(), offer.getTitle(), offer.getDescription(), offer.getItemName(),
                offer.getItemTypeId(), offer.getItemVariant(), amount,
                offer.getBasePrice(), (long) Math.floor(offer.getBasePrice() * amount),
                (long) Math.ceil(offer.getBasePrice() * amount),
                offer.getCurrencyIdentifier(), offer.getIcon(), offer.getCategory(), offer.getSource(),
                offer.getPluginIdentifier(), offer.isEnabled(), false, offer.isSystemOffer(),
                offer.getDefaultStock(), offer.getDefaultTargetStock(), offer.getDefaultStockLimit(),
                offer.getDefaultDrainRate(), offer.getDefaultRefillRate(), offer.getStockMode(),
                offer.getMinPriceMultiplier(), offer.getMaxPriceMultiplier(), offer.getSpreadPercent(),
                offer.getDrainPercent(), offer.getDrainMax(), offer.getRestockPercent(), offer.getRestockMax(),
                offer.getPerPlayerDailySellLimit(), offer.getGlobalDailySellLimit(), offer.getCallback(),
                offer.getPriceResolver());
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

    private record StackChange(Item item, int oldStack) {
    }

    private record CreatedSlot(int slot, SlotType slotType) {
    }

    private record ItemState(int durability, short status, String modifier) {
    }

    private record SellSelection(int slot, SlotType slotType, int originalStack, int amount, ItemState state,
            int maxDurability, String modifier, double modifierMultiplier, double normalPayout, long basePayout,
            long adjustedPayout) {
    }

    public static final class SellQuote {
        private final List<SellSelection> selections;
        private final long payout;
        private final int amount;
        private final String message;

        private SellQuote(List<SellSelection> selections, long payout, int amount, String message) {
            this.selections = List.copyOf(selections);
            this.payout = payout;
            this.amount = amount;
            this.message = message == null ? "" : message;
        }

        public static SellQuote invalid(String message) { return new SellQuote(List.of(), 0L, 0, message); }
        List<SellSelection> selections() { return selections; }
        public long payout() { return payout; }
        /** Trader-funded amount is capped at the equivalent Normal-modifier payout. */
        public long traderPayoutCap() {
            double normalPayout = 0.0d;
            for (SellSelection selection : selections) {
                normalPayout = Math.min(Double.MAX_VALUE - normalPayout,
                        normalPayout + Math.max(0.0d, selection.normalPayout()));
            }
            return ShopService.traderPayoutCap(payout, normalPayout);
        }
        public long worldModifierPremium() { return Math.max(0L, payout - traderPayoutCap()); }
        public int amount() { return amount; }
        public String message() { return message; }
        public boolean sellable() { return !selections.isEmpty() && amount > 0; }
        public boolean requiresConditionConfirmation() {
            return selections.stream().anyMatch(selection -> selection.maxDurability() > 0
                    || !"Normal".equals(selection.modifier()));
        }
        public List<SellQuoteLine> lines() {
            return selections.stream().map(selection -> new SellQuoteLine(selection.amount(),
                    selection.state().durability(), selection.maxDurability(), selection.modifier(),
                    selection.modifierMultiplier(), selection.basePayout(), selection.adjustedPayout()))
                    .toList();
        }
        ShopOffer payoutOffer(ShopOffer offer) {
            return offer.economyCopy(amount, offer.getBasePrice(), payout, offer.getSellPrice());
        }
    }

    public record SellQuoteLine(int amount, int durability, int maxDurability, String modifier,
            double modifierMultiplier, long basePayout, long payout) {
    }
}
