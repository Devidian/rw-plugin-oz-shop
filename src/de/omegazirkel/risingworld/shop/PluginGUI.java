package de.omegazirkel.risingworld.shop;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.objects.Player;

public class PluginGUI {
    private static PluginGUI instance = null;
    private Shop plugin;
    private final Colors c = Colors.getInstance();

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Shop plugin) {
        AssetManager.loadIconFromPlugin(plugin, "shop-icon");
        PluginGUI gui = getInstance();
        gui.plugin = plugin;
        PluginMenuManager.registerPluginMenu(new MenuItem(AssetManager.getIcon("shop-icon"), "Shop", gui::openMainMenu));
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openMainMenu(Player uiPlayer) {
        if (plugin != null) {
            showShopMenu(uiPlayer);
        }
    }

    private void showShopMenu(Player player) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(AssetManager.getIcon("shop-icon"), t(player, "TC_MENU_SHOP_LIST"), p -> {
            p.hideRadialMenu(true);
            if (!plugin.isShopAvailableFor(p)) {
                p.sendTextMessage(c.warning + plugin.shopUnavailableMessage(p));
                return;
            }
            plugin.openShopUI(p);
        }));
        if (player.isAdmin()) {
            items.add(new MenuItem(AssetManager.getIcon("shop-icon"), t(player, "TC_MENU_SHOP_ZONE_CREATE"),
                    this::createOrEnableZone));
        }
        items.add(MenuItem.closeMenu(player));
        PluginMenuManager.showMenu(player, items);
    }

    private void createOrEnableZone(Player player) {
        player.hideRadialMenu(true);
        plugin.shopZoneService().createOrEnableCurrentZone(player).ifPresentOrElse(zone -> {
            player.sendTextMessage(c.okay + t(player, "TC_SHOP_ZONE_CREATED")
                    .replace("PH_AREA", zone.getAreaName())
                    .replace("PH_AREA_ID", String.valueOf(zone.getAreaId())));
        }, () -> player.sendTextMessage(c.warning + t(player, "TC_SHOP_ZONE_NO_AREA")));
    }

    private String t(Player player, String key) {
        return I18n.getInstance(plugin).get(key, player);
    }
}
