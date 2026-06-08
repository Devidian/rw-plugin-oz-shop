package de.omegazirkel.risingworld.shop;

public enum ShopStockMode {
    STATIC,
    PLAYER_SUPPLIED,
    SYSTEM_SUPPLIED,
    HYBRID;

    public static ShopStockMode from(String value) {
        if (value == null || value.trim().isBlank()) {
            return STATIC;
        }
        try {
            return ShopStockMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return STATIC;
        }
    }
}
