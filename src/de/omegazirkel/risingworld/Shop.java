package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.util.List;

import de.omegazirkel.risingworld.shop.PluginSettings;
import de.omegazirkel.risingworld.shop.ShopErrorCode;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopOfferRegistrationResult;
import de.omegazirkel.risingworld.shop.ShopPurchaseCallback;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.shop.ShopService;
import de.omegazirkel.risingworld.shop.SystemOfferFile;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.shop.ui.ShopPlayerPluginData;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.FileChangeListener;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;

public class Shop extends Plugin implements Listener, FileChangeListener {
    static final Colors c = Colors.getInstance();
    private static I18n t;
    private static PluginSettings s;
    private static ShopService service;
    public static String name;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Shop");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        s = PluginSettings.getInstance(this);
        t = I18n.getInstance(this);
        s.initSettings();
        service = new ShopService(new WalletBridge(this));
        reloadSystemOffers();
        registerEventListener(this);

        PluginMenuManager.registerPluginMenu(new MenuItem(AssetManager.getIcon("shop-icon"), "Shop", p -> {
            p.hideRadialMenu(true);
            sendOfferList(p);
        }));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new ShopPlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.clear();
        }
    }

    @Override
    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        logger().setLevel(s.logLevel);
        reloadSystemOffers();
    }

    @EventMethod
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (s.enableWelcomeMessage) {
            player.sendTextMessage(t.get("TC_MSG_PLUGIN_WELCOME", player)
                    .replace("PH_PLUGIN_NAME", getDescription("name"))
                    .replace("PH_PLUGIN_CMD", s.shopCommand)
                    .replace("PH_PLUGIN_VERSION", getDescription("version")));
        }
        if (player.isAdmin() && !service.walletAvailable()) {
            player.sendTextMessage(c.warning + t.get("TC_SHOP_WARN_WALLET_MISSING", player));
        }
    }

    @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] cmdParts = event.getCommand().split(" ", 3);
        if (!cmdParts[0].equals("/" + s.shopCommand)) {
            return;
        }
        if (cmdParts.length == 1 || cmdParts[1].equalsIgnoreCase("list")) {
            sendOfferList(player);
            return;
        }
        if (cmdParts[1].equalsIgnoreCase("buy") && cmdParts.length == 3) {
            ShopPurchaseResult result = purchase(player, cmdParts[2]);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            return;
        }
        if (cmdParts[1].equalsIgnoreCase("reload") && player.isAdmin()) {
            int count = reloadSystemOffers();
            player.sendTextMessage(c.okay + t.get("TC_SHOP_RELOADED", player).replace("PH_COUNT", String.valueOf(count)));
            return;
        }
        player.sendTextMessage(c.warning + t.get("TC_SHOP_USAGE", player).replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    public ShopOfferRegistrationResult registerOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String pluginIdentifier,
            ShopPurchaseCallback callback) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, pluginIdentifier,
                callback);
    }

    public ShopOfferRegistrationResult unregisterOffer(String id, String pluginIdentifier) {
        return service.unregisterOffer(id, pluginIdentifier);
    }

    public ShopPurchaseResult purchase(Player player, String offerId) {
        return service.purchase(player, offerId);
    }

    public ShopOffer findOffer(String offerId) {
        return service.findOffer(offerId).orElse(null);
    }

    public List<ShopOffer> listOffers() {
        return service.listOffers();
    }

    public int reloadSystemOffers() {
        if (service == null || s == null) {
            return 0;
        }
        List<ShopOffer> offers = SystemOfferFile.load(this, s.systemOffersFile);
        service.replaceSystemOffers(offers);
        return offers.size();
    }

    private void sendOfferList(Player player) {
        List<ShopOffer> offers = listOffers().stream().filter(ShopOffer::isEnabled).toList();
        if (offers.isEmpty()) {
            player.sendTextMessage(c.info + t.get("TC_SHOP_NO_OFFERS", player));
            return;
        }
        player.sendTextMessage(c.okay + t.get("TC_SHOP_TITLE", player));
        for (ShopOffer offer : offers) {
            String currency = offer.getCurrencyIdentifier().isBlank()
                    ? t.get("TC_SHOP_DEFAULT_CURRENCY", player)
                    : offer.getCurrencyIdentifier();
            String label = offer.getItemName().isBlank() ? offer.getTitle()
                    : offer.getAmount() + "x " + offer.getItemName() + ":" + offer.getItemVariant();
            player.sendTextMessage(c.info + offer.getId() + c.text + " - " + label + " ("
                    + offer.getPrice() + " " + currency + ")");
        }
        player.sendTextMessage(c.text + t.get("TC_SHOP_USAGE", player).replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    public static ShopPurchaseResult unavailable(String message) {
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, message);
    }
}
