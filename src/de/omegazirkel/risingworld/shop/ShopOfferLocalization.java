package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

/** Player-language texts for plugin-provided Shop offers. */
public interface ShopOfferLocalization {
    String title(Player player);
    String description(Player player);
}
