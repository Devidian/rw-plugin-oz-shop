package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

public class ShopOffer {
    private final String id;
    private final String title;
    private final String description;
    private final String itemName;
    private final short itemTypeId;
    private final int itemVariant;
    private final int amount;
    private final double basePrice;
    private final long buyPrice;
    private final long sellPrice;
    private final String currencyIdentifier;
    private final String icon;
    private final String category;
    private final String source;
    private final String pluginIdentifier;
    private final boolean enabled;
    private final boolean systemOffer;
    private final long defaultStock;
    private final long defaultTargetStock;
    private final long defaultStockLimit;
    private final double defaultDrainRate;
    private final double defaultRefillRate;
    private final ShopStockMode stockMode;
    private final double minPriceMultiplier;
    private final double maxPriceMultiplier;
    private final double spreadPercent;
    private final double drainPercent;
    private final long drainMax;
    private final double restockPercent;
    private final long restockMax;
    private final long perPlayerDailySellLimit;
    private final long globalDailySellLimit;
    private final ShopPurchaseCallback callback;
    private final ShopPriceResolver priceResolver;
    private ShopOfferLocalization localization;

    public ShopOffer(String id, String title, String description, long price, String currencyIdentifier, String icon,
            String pluginIdentifier, boolean enabled, boolean systemOffer, ShopPurchaseCallback callback) {
        this(id, title, description, "", (short) 0, 0, 1, price, currencyIdentifier, icon, pluginIdentifier, enabled,
                systemOffer, callback, null);
    }

    public ShopOffer(String id, String title, String description, long price, String currencyIdentifier, String icon,
            String pluginIdentifier, boolean enabled, boolean systemOffer, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        this(id, title, description, "", (short) 0, 0, 1, price, currencyIdentifier, icon, pluginIdentifier, enabled,
                systemOffer, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, long price, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean enabled, boolean systemOffer,
            ShopPurchaseCallback callback, ShopPriceResolver priceResolver) {
        this(id, title, description, "", (short) 0, 0, 1, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, enabled, systemOffer, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, int itemVariant, int amount, long price,
            String currencyIdentifier, String icon, String pluginIdentifier, boolean enabled, boolean systemOffer,
            ShopPurchaseCallback callback) {
        this(id, title, description, itemName, (short) 0, itemVariant, amount, price, currencyIdentifier, icon,
                pluginIdentifier, enabled, systemOffer, callback, null);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, long price, String currencyIdentifier, String icon, String pluginIdentifier, boolean enabled,
            boolean systemOffer, ShopPurchaseCallback callback) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, price, currencyIdentifier, icon,
                pluginIdentifier, enabled, systemOffer, callback, null);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, long price, String currencyIdentifier, String icon, String pluginIdentifier, boolean enabled,
            boolean systemOffer, ShopPurchaseCallback callback, ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, price, currencyIdentifier, icon, "",
                pluginIdentifier, pluginIdentifier, enabled, systemOffer, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, long price, String currencyIdentifier, String icon, String category, String source,
            String pluginIdentifier, boolean enabled, boolean systemOffer, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, price, price, price,
                currencyIdentifier, icon, category, source, pluginIdentifier, false, enabled, systemOffer, callback,
                priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, double basePrice, long buyPrice, long sellPrice, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean buyEnabled, boolean sellEnabled,
            boolean systemOffer, ShopPurchaseCallback callback, ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, basePrice, buyPrice, sellPrice,
                currencyIdentifier, icon, category, source, pluginIdentifier, buyEnabled, sellEnabled, systemOffer,
                0L, 0.0d, 0.0d, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, double basePrice, long buyPrice, long sellPrice, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean buyEnabled, boolean sellEnabled,
            boolean systemOffer, long defaultStock, double defaultDrainRate, double defaultRefillRate,
            ShopPurchaseCallback callback, ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, basePrice, buyPrice, sellPrice,
                currencyIdentifier, icon, category, source, pluginIdentifier, buyEnabled, sellEnabled, systemOffer,
                defaultStock, 0L, 0L, defaultDrainRate, defaultRefillRate, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, double basePrice, long buyPrice, long sellPrice, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean buyEnabled, boolean sellEnabled,
            boolean systemOffer, long defaultStock, long defaultTargetStock, long defaultStockLimit,
            double defaultDrainRate, double defaultRefillRate, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, basePrice, buyPrice, sellPrice,
                currencyIdentifier, icon, category, source, pluginIdentifier, buyEnabled, sellEnabled, systemOffer,
                defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate, defaultRefillRate,
                ShopStockMode.STATIC, 0.25d, 4.0d, 25.0d, 0.0d, 0L, 0.0d, 0L, 0L, 0L, callback, priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, double basePrice, long buyPrice, long sellPrice, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean buyEnabled, boolean sellEnabled,
            boolean systemOffer, long defaultStock, long defaultTargetStock, long defaultStockLimit,
            double defaultDrainRate, double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier,
            double maxPriceMultiplier, double spreadPercent, ShopPurchaseCallback callback,
            ShopPriceResolver priceResolver) {
        this(id, title, description, itemName, itemTypeId, itemVariant, amount, basePrice, buyPrice, sellPrice,
                currencyIdentifier, icon, category, source, pluginIdentifier, buyEnabled, sellEnabled, systemOffer,
                defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate, defaultRefillRate, stockMode,
                minPriceMultiplier, maxPriceMultiplier, spreadPercent, 0.0d, 0L, 0.0d, 0L, 0L, 0L, callback,
                priceResolver);
    }

    public ShopOffer(String id, String title, String description, String itemName, short itemTypeId, int itemVariant,
            int amount, double basePrice, long buyPrice, long sellPrice, String currencyIdentifier, String icon,
            String category, String source, String pluginIdentifier, boolean buyEnabled, boolean sellEnabled,
            boolean systemOffer, long defaultStock, long defaultTargetStock, long defaultStockLimit,
            double defaultDrainRate, double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier,
            double maxPriceMultiplier, double spreadPercent, double drainPercent, long drainMax,
            double restockPercent, long restockMax, long perPlayerDailySellLimit, long globalDailySellLimit,
            ShopPurchaseCallback callback, ShopPriceResolver priceResolver) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.itemName = itemName == null ? "" : itemName.trim();
        this.itemTypeId = itemTypeId;
        this.itemVariant = itemVariant;
        this.amount = amount;
        this.basePrice = basePrice;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.currencyIdentifier = currencyIdentifier == null ? "" : currencyIdentifier.trim().toUpperCase();
        this.icon = icon == null ? "" : icon.trim();
        this.category = category == null ? "" : category.trim();
        this.source = source == null ? "" : source.trim();
        this.pluginIdentifier = pluginIdentifier == null ? "" : pluginIdentifier.trim();
        this.enabled = buyEnabled || sellEnabled;
        this.systemOffer = systemOffer;
        this.defaultStock = Math.max(0L, defaultStock);
        this.defaultTargetStock = Math.max(0L, defaultTargetStock);
        this.defaultStockLimit = Math.max(0L, defaultStockLimit);
        this.defaultDrainRate = Math.max(0.0d, defaultDrainRate);
        this.defaultRefillRate = Math.max(0.0d, defaultRefillRate);
        this.stockMode = stockMode == null ? ShopStockMode.STATIC : stockMode;
        this.minPriceMultiplier = minPriceMultiplier > 0.0d ? minPriceMultiplier : 0.25d;
        this.maxPriceMultiplier = maxPriceMultiplier >= this.minPriceMultiplier ? maxPriceMultiplier : 4.0d;
        this.spreadPercent = Math.max(0.0d, spreadPercent);
        this.drainPercent = Math.max(0.0d, drainPercent);
        this.drainMax = Math.max(0L, drainMax);
        this.restockPercent = Math.max(0.0d, restockPercent);
        this.restockMax = Math.max(0L, restockMax);
        this.perPlayerDailySellLimit = Math.max(0L, perPlayerDailySellLimit);
        this.globalDailySellLimit = Math.max(0L, globalDailySellLimit);
        this.callback = callback;
        this.priceResolver = priceResolver;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTitle(Player player) {
        return localization == null ? title : localized(localization.title(player), title);
    }
    public String getDescription(Player player) {
        return localization == null ? description : localized(localization.description(player), description);
    }
    public String getItemName() { return itemName; }
    public short getItemTypeId() { return itemTypeId; }
    public int getItemVariant() { return itemVariant; }
    public int getAmount() { return amount; }
    public double getBasePrice() { return basePrice; }
    public long getBuyPrice() { return buyPrice; }
    public long getSellPrice() { return sellPrice; }
    public long getPrice() { return sellPrice; }
    public long getPrice(Player player) {
        return priceResolver == null ? sellPrice : Math.max(0, priceResolver.price(player, this));
    }
    public String getCurrencyIdentifier() { return currencyIdentifier; }
    public String getIcon() { return icon; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    /**
     * @deprecated use {@link #canPlayerSellToSystem()} for new code.
     */
    @Deprecated
    public boolean isBuyEnabled() { return canPlayerSellToSystem(); }
    /**
     * @deprecated use {@link #canPlayerBuyFromSystem()} for new code.
     */
    @Deprecated
    public boolean isSellEnabled() { return canPlayerBuyFromSystem(); }
    public boolean isEnabled() { return enabled; }
    public boolean canPlayerBuyFromSystem() { return enabled; }
    public boolean canPlayerSellToSystem() {
        return enabled && (stockMode == ShopStockMode.PLAYER_SUPPLIED || stockMode == ShopStockMode.HYBRID);
    }
    public boolean usesPlayerSellLimits() { return canPlayerSellToSystem(); }
    public boolean isSystemOffer() { return systemOffer; }
    public long getDefaultStock() { return defaultStock; }
    public long getDefaultTargetStock() { return defaultTargetStock; }
    public long getDefaultStockLimit() { return defaultStockLimit; }
    public double getDefaultDrainRate() { return defaultDrainRate; }
    public double getDefaultRefillRate() { return defaultRefillRate; }
    public ShopStockMode getStockMode() { return stockMode; }
    public double getMinPriceMultiplier() { return minPriceMultiplier; }
    public double getMaxPriceMultiplier() { return maxPriceMultiplier; }
    public double getSpreadPercent() { return spreadPercent; }
    public double getDrainPercent() { return drainPercent; }
    public long getDrainMax() { return drainMax; }
    public double getRestockPercent() { return restockPercent; }
    public long getRestockMax() { return restockMax; }
    public long getPerPlayerDailySellLimit() { return perPlayerDailySellLimit; }
    public long getGlobalDailySellLimit() { return globalDailySellLimit; }
    ShopPurchaseCallback getCallback() { return callback; }
    ShopPriceResolver getPriceResolver() { return priceResolver; }
    void setLocalization(ShopOfferLocalization localization) { this.localization = localization; }
    private static String localized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public ShopOffer economyCopy(int amount, double basePrice, long buyPrice, long sellPrice) {
        return new ShopOffer(id, title, description, itemName, itemTypeId, itemVariant, Math.max(1, amount),
                Math.max(0.0d, basePrice), Math.max(0L, buyPrice), Math.max(0L, sellPrice), currencyIdentifier, icon,
                category, source, pluginIdentifier, enabled, false, systemOffer, defaultStock,
                defaultTargetStock, defaultStockLimit, defaultDrainRate, defaultRefillRate, stockMode,
                minPriceMultiplier, maxPriceMultiplier, spreadPercent, drainPercent, drainMax, restockPercent,
                restockMax, perPlayerDailySellLimit, globalDailySellLimit, callback, priceResolver);
    }

    public ShopOffer economyConfigCopy(long defaultTargetStock, long defaultStockLimit, double defaultDrainRate,
            double defaultRefillRate, ShopStockMode stockMode, double minPriceMultiplier, double maxPriceMultiplier,
            double spreadPercent, double drainPercent, long drainMax, double restockPercent, long restockMax,
            long perPlayerDailySellLimit, long globalDailySellLimit) {
        return new ShopOffer(id, title, description, itemName, itemTypeId, itemVariant, amount, basePrice,
                buyPrice, sellPrice, currencyIdentifier, icon, category, source, pluginIdentifier, enabled,
                false, systemOffer, defaultStock, defaultTargetStock, defaultStockLimit, defaultDrainRate,
                defaultRefillRate, stockMode, minPriceMultiplier, maxPriceMultiplier, spreadPercent, drainPercent,
                drainMax, restockPercent, restockMax, perPlayerDailySellLimit, globalDailySellLimit, callback,
                priceResolver);
    }
}
