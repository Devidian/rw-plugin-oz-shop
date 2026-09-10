package de.omegazirkel.risingworld.shop.exports;

/** Current public trader offer state. Price is the amount a player pays for amount items. */
public record ShopOfferExport(String id, String itemName, int itemVariant, int amount, long price, long stock,
        String currency) {
}
