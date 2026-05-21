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
    private final long price;
    private final String currencyIdentifier;
    private final String icon;
    private final String category;
    private final String source;
    private final String pluginIdentifier;
    private final boolean enabled;
    private final boolean systemOffer;
    private final ShopPurchaseCallback callback;
    private final ShopPriceResolver priceResolver;

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
        this.id = id;
        this.title = title;
        this.description = description;
        this.itemName = itemName == null ? "" : itemName.trim();
        this.itemTypeId = itemTypeId;
        this.itemVariant = itemVariant;
        this.amount = amount;
        this.price = price;
        this.currencyIdentifier = currencyIdentifier == null ? "" : currencyIdentifier.trim().toUpperCase();
        this.icon = icon == null ? "" : icon.trim();
        this.category = category == null ? "" : category.trim();
        this.source = source == null ? "" : source.trim();
        this.pluginIdentifier = pluginIdentifier == null ? "" : pluginIdentifier.trim();
        this.enabled = enabled;
        this.systemOffer = systemOffer;
        this.callback = callback;
        this.priceResolver = priceResolver;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getItemName() { return itemName; }
    public short getItemTypeId() { return itemTypeId; }
    public int getItemVariant() { return itemVariant; }
    public int getAmount() { return amount; }
    public long getPrice() { return price; }
    public long getPrice(Player player) {
        return priceResolver == null ? price : Math.max(0, priceResolver.price(player, this));
    }
    public String getCurrencyIdentifier() { return currencyIdentifier; }
    public String getIcon() { return icon; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    public boolean isEnabled() { return enabled; }
    public boolean isSystemOffer() { return systemOffer; }
    ShopPurchaseCallback getCallback() { return callback; }
    ShopPriceResolver getPriceResolver() { return priceResolver; }
}
