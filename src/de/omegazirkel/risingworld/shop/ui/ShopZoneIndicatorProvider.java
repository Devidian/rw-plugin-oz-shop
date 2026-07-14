package de.omegazirkel.risingworld.shop.ui;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.ui.SharedIndicatorProvider;
import net.risingworld.api.objects.Player;

public class ShopZoneIndicatorProvider implements SharedIndicatorProvider {
    private final Shop plugin;

    public ShopZoneIndicatorProvider(Shop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean showIndicator(Player player) {
        return plugin.showShopZoneIndicator() && plugin.currentShopZone(player).isPresent();
    }

    @Override
    public String getIcon(Player player) {
        return "zone-shop-indicator";
    }
}
