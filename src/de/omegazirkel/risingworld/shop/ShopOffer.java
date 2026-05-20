package de.omegazirkel.risingworld.shop;

public class ShopOffer {
    private final String id;
    private final String title;
    private final String description;
    private final long price;
    private final String currencyIdentifier;
    private final String icon;
    private final String pluginIdentifier;
    private final boolean enabled;
    private final boolean systemOffer;
    private final ShopPurchaseCallback callback;

    public ShopOffer(String id, String title, String description, long price, String currencyIdentifier, String icon,
            String pluginIdentifier, boolean enabled, boolean systemOffer, ShopPurchaseCallback callback) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.currencyIdentifier = currencyIdentifier == null ? "" : currencyIdentifier.trim().toUpperCase();
        this.icon = icon == null ? "" : icon.trim();
        this.pluginIdentifier = pluginIdentifier == null ? "" : pluginIdentifier.trim();
        this.enabled = enabled;
        this.systemOffer = systemOffer;
        this.callback = callback;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
    public String getCurrencyIdentifier() { return currencyIdentifier; }
    public String getIcon() { return icon; }
    public String getPluginIdentifier() { return pluginIdentifier; }
    public boolean isEnabled() { return enabled; }
    public boolean isSystemOffer() { return systemOffer; }
    ShopPurchaseCallback getCallback() { return callback; }
}
