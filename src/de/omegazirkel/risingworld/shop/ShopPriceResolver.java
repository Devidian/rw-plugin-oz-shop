package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

public interface ShopPriceResolver {
    long price(Player player, ShopOffer offer);
}
