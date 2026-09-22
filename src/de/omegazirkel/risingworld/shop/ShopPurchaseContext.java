package de.omegazirkel.risingworld.shop;

import net.risingworld.api.objects.Player;

/** Immutable, paid purchase data for opt-in plugin offers. */
public record ShopPurchaseContext(Player player, ShopOffer offer, long price, String currencyIdentifier,
        String correlationId, String payerSystemAccountId, String payerPluginIdentifier) { }
