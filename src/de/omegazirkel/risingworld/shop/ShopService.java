package de.omegazirkel.risingworld.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
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

        WalletBridge.WalletCallResult payment = WalletBridge.WalletCallResult.success("No payment required.");
        if (price > 0) {
            payment = effectiveOffer.getCurrencyIdentifier().isBlank()
                    ? wallet.withdrawDefault(player.getDbID(), price, "Shop purchase: " + effectiveOffer.getId(),
                            "OZ - Shop")
                    : wallet.withdraw(player.getDbID(), price, "Shop purchase: " + effectiveOffer.getId(),
                            effectiveOffer.getCurrencyIdentifier(), "OZ - Shop");
            if (!payment.success()) {
                Shop.logger().error(payment.toString());
                return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, payment.message());
            }
        }

        try {
            ShopPurchaseResult callbackResult = effectiveOffer.getCallback().complete(player, effectiveOffer);
            if (callbackResult == null) {
                return failAfterPayment(player, effectiveOffer, price, "Purchase action returned no result after payment.");
            }
            if (!callbackResult.success && price > 0) {
                return failAfterPayment(player, effectiveOffer, price, callbackResult.message);
            }
            return callbackResult;
        } catch (RuntimeException ex) {
            Shop.logger().error(ex.getMessage());
            return failAfterPayment(player, effectiveOffer, price, "Purchase action failed after payment: " + ex.getMessage());
        }
    }

    public ShopPurchaseResult sell(Player player, ShopOffer offer, int quantity) {
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
        long payout = effectiveOffer.getBuyPrice();
        if (payout < 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.PRICE_RESOLUTION_FAILED,
                    "Could not resolve offer payout.");
        }
        ShopPurchaseResult removed = removeItems(player, effectiveOffer);
        if (!removed.success) {
            return removed;
        }
        WalletBridge.WalletCallResult deposit = effectiveOffer.getCurrencyIdentifier().isBlank()
                ? wallet.depositDefault(player.getDbID(), payout, "Shop sale: " + effectiveOffer.getId(),
                        "OZ - Shop")
                : wallet.deposit(player.getDbID(), payout, "Shop sale: " + effectiveOffer.getId(),
                        effectiveOffer.getCurrencyIdentifier(), "OZ - Shop");
        if (!deposit.success()) {
            ShopPurchaseResult returned = addSystemOfferItem(player, effectiveOffer);
            if (!returned.success) {
                Shop.logger().error("Shop sale rollback failed for " + effectiveOffer.getId()
                        + ": could not return removed items.");
            }
            return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, deposit.message());
        }
        return ShopPurchaseResult.success("Sale completed.", effectiveOffer);
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

    private ShopPurchaseResult removeItems(Player player, ShopOffer offer) {
        Inventory inventory = player.getInventory();
        if (countMatchingItems(inventory, offer) < offer.getAmount()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                    "You do not have enough matching items in your inventory.");
        }
        int remaining = offer.getAmount();
        for (SlotType slotType : SlotType.values()) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (!matchesSystemOfferItem(item, offer)) {
                    continue;
                }
                int remove = Math.min(remaining, item.getStack());
                if (!inventory.removeItem(slot, slotType, remove)) {
                    return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                            "Could not remove item from inventory.");
                }
                remaining -= remove;
                if (remaining == 0) {
                    inventory.syncWithClient();
                    return ShopPurchaseResult.success("Items removed.", offer);
                }
            }
        }
        return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                "You do not have enough matching items in your inventory.");
    }

    private int countMatchingItems(Inventory inventory, ShopOffer offer) {
        int amount = 0;
        for (SlotType slotType : SlotType.values()) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (matchesSystemOfferItem(item, offer)) {
                    amount += Math.max(0, item.getStack());
                }
            }
        }
        return amount;
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
        if (item == null || !item.isValid()) {
            return false;
        }
        ObjectDefinition objectDefinition = objectDefinition(offer);
        if (objectDefinition != null && item instanceof Item.ObjectItem objectItem) {
            String objectName = objectItem.getObjectName();
            return objectName != null && objectName.equalsIgnoreCase(objectDefinition.name)
                    && item.getVariant() == objectVariant(offer, objectDefinition);
        }
        ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(offer.getItemName());
        if (constructionDefinition != null && item instanceof Item.ConstructionItem constructionItem) {
            String constructionName = constructionItem.getConstructionName();
            return constructionName != null && constructionName.equalsIgnoreCase(constructionDefinition.name)
                    && item.getVariant() == offer.getItemVariant();
        }
        ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(offer.getItemName());
        if (clothingDefinition != null && item instanceof Item.ClothingItem clothingItem) {
            String clothingName = clothingItem.getClothingName();
            return clothingName != null && clothingName.equalsIgnoreCase(clothingDefinition.name)
                    && item.getVariant() == offer.getItemVariant();
        }
        PlantDefinition plantDefinition = Definitions.getPlantDefinition(offer.getItemName());
        if (plantDefinition != null) {
            String itemName = item.getName();
            return itemName != null && itemName.equalsIgnoreCase(plantDefinition.name)
                    && item.getVariant() == offer.getItemVariant();
        }
        return item.getTypeID() == offer.getItemTypeId() && item.getVariant() == offer.getItemVariant();
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
}
