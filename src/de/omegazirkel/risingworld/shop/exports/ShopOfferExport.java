package de.omegazirkel.risingworld.shop.exports;

/** Current public trader offer state, including both player purchase and sell-back directions. */
public record ShopOfferExport(String id, String itemName, int itemVariant, int amount, long price, long stock,
        String currency, long playerBuyPrice, long playerSellPrice, long maxStock, String nameDe, String nameEn) {
}
