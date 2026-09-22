package de.omegazirkel.risingworld.shop;

@FunctionalInterface
public interface ShopPurchaseContextCallback {
    ShopPurchaseResult complete(ShopPurchaseContext context);
}
