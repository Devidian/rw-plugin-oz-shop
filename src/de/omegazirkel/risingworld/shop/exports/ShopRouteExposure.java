package de.omegazirkel.risingworld.shop.exports;

import de.omegazirkel.risingworld.shop.PluginSettings;

public record ShopRouteExposure(boolean zones) {

    public static ShopRouteExposure from(PluginSettings settings) {
        return new ShopRouteExposure(settings.exposeShopZones);
    }
}
