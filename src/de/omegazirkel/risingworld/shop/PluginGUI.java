package de.omegazirkel.risingworld.shop;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.MessageBoxButtons;

public class PluginGUI {
    private static PluginGUI instance = null;
    private Shop plugin;
    private final Colors c = Colors.getInstance();

    private PluginGUI() {
    }

    public static PluginGUI getInstance(Shop plugin) {
        AssetManager.loadIconFromPlugin(plugin, "oz-shop");
        AssetManager.loadIconFromPlugin(plugin, "zone-shop-indicator");
        AssetManager.loadIconFromPlugin(plugin, "create-trader-male");
        AssetManager.loadIconFromPlugin(plugin, "create-trader-female");
        AssetManager.loadIconFromPlugin(plugin, "create-faction-trader-male");
        AssetManager.loadIconFromPlugin(plugin, "create-faction-trader-female");
        PluginGUI gui = getInstance();
        gui.plugin = plugin;
        PluginMenuManager.registerPluginMenu(new MenuItem(Shop.name, "oz-shop", "Shop",
                gui::openMainMenu));
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openMainMenu(Player uiPlayer) {
        if (plugin == null) {
            return;
        }
        showShopMenu(uiPlayer);
    }

    private void showShopMenu(Player player) {
        List<MenuItem> items = new ArrayList<>();
        if (player.isAdmin() || plugin.isShopAvailableFor(player)) {
            items.add(new MenuItem("oz-shop", t(player, "tc.menu.shop.list"),
                    this::openDirectShop));
        }
        if (player.isAdmin() && plugin.currentShopZone(player).isEmpty()) {
            items.add(new MenuItem("oz-shop", t(player, "tc.menu.shop.zone.create"),
                    this::createOrEnableZone));
        }
        if (player.isAdmin()) {
            items.add(MenuItem.iconKey("create-trader-male", t(player, "tc.menu.shop.trader.create.male"),
                    p -> createTrader(p, true)));
            items.add(MenuItem.iconKey("create-trader-female", t(player, "tc.menu.shop.trader.create.female"),
                    p -> createTrader(p, false)));
        }
        if (plugin.canCreateFactionTrader(player)) {
            items.add(MenuItem.iconKey("create-faction-trader-male", t(player, "tc.menu.shop.trader.create.faction.male"),
                    p -> createFactionTrader(p, true)));
            items.add(MenuItem.iconKey("create-faction-trader-female", t(player, "tc.menu.shop.trader.create.faction.female"),
                    p -> createFactionTrader(p, false)));
        }
        items.add(new MenuItem("info-status", t(player, "tc.menu.shop.info.status"), p -> {
            p.hideRadialMenu(true);
            PluginInfoStatusProviders.show(p, Shop.name);
        }));
        items.add(MenuItem.closeMenu(player));
        PluginMenuManager.showMenu(player, items);
    }

    private void openDirectShop(Player player) {
        player.hideRadialMenu(true);
        if (player.isAdmin() && !plugin.isShopAvailableFor(player)) {
            plugin.openShopUI(player);
            return;
        }
        if (!plugin.isShopAvailableFor(player)) {
            player.sendTextMessage(c.warning + plugin.shopUnavailableMessage(player));
            return;
        }
        plugin.openShopUI(player);
    }

    private void createOrEnableZone(Player player) {
        player.hideRadialMenu(true);
        plugin.createOrEnableCurrentZone(player).ifPresentOrElse(zone -> {
            player.sendTextMessage(c.okay + t(player, "tc.shop.zone.created")
                    .replace("PH_AREA", zone.getAreaName())
                    .replace("PH_AREA_ID", String.valueOf(zone.getAreaId())));
        }, () -> player.sendTextMessage(c.warning + t(player, "tc.shop.zone.no.area")));
    }

    private void createTrader(Player player, boolean male) {
        player.hideRadialMenu(true);
        showTraderCreationConfirmation(player, false, male);
    }

    private void createFactionTrader(Player player, boolean male) {
        player.hideRadialMenu(true);
        showTraderCreationConfirmation(player, true, male);
    }

    private void showTraderCreationConfirmation(Player player, boolean faction, boolean male) {
        String kind = t(player, faction ? (male ? "tc.shop.trader.create.confirm.kind.faction.male"
                : "tc.shop.trader.create.confirm.kind.faction.female")
                : (male ? "tc.shop.trader.create.confirm.kind.male" : "tc.shop.trader.create.confirm.kind.female"));
        player.closeAllActiveUIWindows();
        OZTools.runAfterModalClose(player, () -> player.showMessageBox(MessageBoxButtons.Yes_No,
                t(player, "tc.shop.trader.create.confirm.title"),
                t(player, "tc.shop.trader.create.confirm.text").replace("PH_KIND", kind), 0,
                answer -> plugin.enqueue(() -> {
                    if (answer != null && answer == 0 && player.isConnected()) {
                        if (faction) plugin.createFactionTrader(player, male);
                        else plugin.createTrader(player, male);
                    }
                })));
    }

    private String t(Player player, String key) {
        return I18n.getInstance(plugin).get(key, player);
    }
}
