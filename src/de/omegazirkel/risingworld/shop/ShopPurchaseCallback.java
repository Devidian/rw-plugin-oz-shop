package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

@FunctionalInterface
public interface ShopPurchaseCallback {
    ShopPurchaseResult complete(Player player, ShopOffer offer);
}
