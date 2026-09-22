package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

/** Optional access and payer policy for a plugin offer. */
@FunctionalInterface
public interface ShopPurchasePolicy {
    ShopPurchaseAuthorization authorize(Player player, ShopOffer offer, long price);
}
