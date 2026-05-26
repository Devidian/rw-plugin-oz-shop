package de.omegazirkel.risingworld.shop;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProvider;
import net.risingworld.api.objects.Player;

public class ShopPluginInfoStatusProvider implements PluginInfoStatusProvider {
    private final Shop plugin;
    private final String pluginName;
    private final String version;

    public ShopPluginInfoStatusProvider(Shop plugin, String version) {
        this.plugin = plugin;
        this.pluginName = Shop.name == null || Shop.name.isBlank() ? "OZ - Shop" : Shop.name;
        this.version = version == null ? "" : version;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public String getInfo(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        return t().get("TC_SHOP_INFO_PANEL_INFO", player)
                .replace("PH_PLUGIN_NAME", pluginName)
                .replace("PH_VERSION", version)
                .replace("PH_PLUGIN_CMD", settings.shopCommand);
    }

    @Override
    public String getStatus(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        ShopZone currentZone = plugin.currentShopZone(player).orElse(null);
        return t().get("TC_SHOP_INFO_PANEL_STATUS", player)
                .replace("PH_WALLET_STATUS", available(plugin.walletAvailable()))
                .replace("PH_SHOP_ENABLED", String.valueOf(settings.shopEnabled))
                .replace("PH_SYSTEM_SHOP_ENABLED", String.valueOf(settings.systemShopEnabled))
                .replace("PH_DEFINITION_EXPORTS", String.valueOf(settings.generateDefinitionExports))
                .replace("PH_DYNAMIC_ECONOMY", String.valueOf(settings.dynamicEconomyEnabled))
                .replace("PH_REQUIRE_SHOP_ZONE", String.valueOf(settings.requireShopZone))
                .replace("PH_CURRENT_ZONE", currentZone == null ? "-" : currentZone.getAreaName())
                .replace("PH_SHOP_ZONES", String.valueOf(plugin.listShopZones().size()))
                .replace("PH_SYSTEM_OFFERS", String.valueOf(plugin.listSystemOffers().size()))
                .replace("PH_PLUGIN_OFFERS", String.valueOf(plugin.listPluginOffers().size()));
    }

    private I18n t() {
        return I18n.getInstance(plugin);
    }

    private static String available(boolean value) {
        return value ? "available" : "missing";
    }
}
