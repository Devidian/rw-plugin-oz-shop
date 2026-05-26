package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.shop.PluginSettings;
import de.omegazirkel.risingworld.shop.ShopErrorCode;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopOfferRegistrationResult;
import de.omegazirkel.risingworld.shop.ShopPluginInfoStatusProvider;
import de.omegazirkel.risingworld.shop.ShopPurchaseCallback;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.shop.ShopService;
import de.omegazirkel.risingworld.shop.ShopZone;
import de.omegazirkel.risingworld.shop.ShopZoneService;
import de.omegazirkel.risingworld.shop.SystemOfferFile;
import de.omegazirkel.risingworld.shop.PluginGUI;
import de.omegazirkel.risingworld.shop.ShopPlayerPreferences;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.shop.ui.ShopOverlay;
import de.omegazirkel.risingworld.shop.ui.ShopPlayerPluginData;
import de.omegazirkel.risingworld.shop.ui.ShopPlayerPluginSettings;
import de.omegazirkel.risingworld.shop.ui.ShopZoneIndicatorProvider;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.FileChangeListener;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import de.omegazirkel.risingworld.tools.db.SQLite;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
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
    private static ShopZoneService zoneService;
    public static String name;
    public static SQLite db;
    public static PlayerSettings ps;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Shop");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        s = PluginSettings.getInstance(this);
        t = I18n.getInstance(this);
        s.initSettings();
        db = new SQLite(this);
        ps = new PlayerSettings(db.getConnection());
        service = new ShopService(new WalletBridge(this));
        zoneService = new ShopZoneService(this, s.shopZonesFile);
        reloadSystemOffers();
        registerEventListener(this);

        PluginGUI.getInstance(this);
        SharedIndicators.registerProvider(name, new ShopZoneIndicatorProvider(this));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(new ShopPlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new ShopPlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders.registerProvider(new ShopPluginInfoStatusProvider(this, getDescription("version")));
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.clear();
        }
        if (name != null) {
            PluginInfoStatusProviders.unregisterProvider(name);
        }
        SharedIndicators.unregisterProvider(name);
    }

    @Override
    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        logger().setLevel(s.logLevel);
        zoneService = new ShopZoneService(this, s.shopZonesFile);
        reloadSystemOffers();
    }

    @EventMethod
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        ShopPlayerPreferences.load(player);
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
        if (cmdParts.length > 1
                && (cmdParts[1].equalsIgnoreCase("status") || cmdParts[1].equalsIgnoreCase("info"))) {
            PluginInfoStatusProviders.show(player, name);
            return;
        }
        if (cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("reload") && player.isAdmin()) {
            int count = reloadSystemOffers();
            reloadShopZones();
            player.sendTextMessage(c.okay + t.get("TC_SHOP_RELOADED", player).replace("PH_COUNT", String.valueOf(count)));
            return;
        }
        if (!isShopAvailableFor(player)) {
            player.sendTextMessage(c.warning + shopUnavailableMessage(player));
            return;
        }
        if (cmdParts.length == 1 || cmdParts[1].equalsIgnoreCase("list")) {
            openShopUI(player);
            return;
        }
        if (cmdParts[1].equalsIgnoreCase("buy") && cmdParts.length == 3) {
            ShopPurchaseResult result = purchase(player, cmdParts[2]);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
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

    public ShopOfferRegistrationResult registerOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String category,
            String source,
            String pluginIdentifier,
            ShopPurchaseCallback callback) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, callback);
    }

    public ShopOfferRegistrationResult registerOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String pluginIdentifier,
            ShopPurchaseCallback callback,
            de.omegazirkel.risingworld.shop.ShopPriceResolver priceResolver) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, pluginIdentifier,
                callback, priceResolver);
    }

    public ShopOfferRegistrationResult registerOffer(
            String id,
            String title,
            String description,
            long price,
            String currencyIdentifier,
            String icon,
            String category,
            String source,
            String pluginIdentifier,
            ShopPurchaseCallback callback,
            de.omegazirkel.risingworld.shop.ShopPriceResolver priceResolver) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, category, source,
                pluginIdentifier, callback, priceResolver);
    }

    public ShopOfferRegistrationResult unregisterOffer(String id, String pluginIdentifier) {
        return service.unregisterOffer(id, pluginIdentifier);
    }

    public int unregisterOffers(String pluginIdentifier) {
        return service.unregisterPluginOffers(pluginIdentifier);
    }

    public ShopPurchaseResult purchase(Player player, String offerId) {
        ShopOffer offer = findOffer(offerId);
        if (offer != null && offer.isSystemOffer() && !isSystemShopAvailableFor(player)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("TC_SHOP_SYSTEM_DISABLED", player));
        }
        return service.purchase(player, offerId);
    }

    public ShopOffer findOffer(String offerId) {
        return service.findOffer(offerId).orElse(null);
    }

    public List<ShopOffer> listOffers() {
        return service.listOffers();
    }

    public List<ShopOffer> listPluginOffers() {
        return service.listPluginOffers();
    }

    public List<ShopOffer> listSystemOffers() {
        return service.listSystemOffers();
    }

    public int reloadSystemOffers() {
        if (service == null || s == null) {
            return 0;
        }
        List<ShopOffer> offers = SystemOfferFile.load(this, s.systemOffersFile, s.generateDefinitionExports);
        service.replaceSystemOffers(offers);
        return offers.size();
    }

    public void reloadShopZones() {
        if (zoneService != null) {
            zoneService.load();
        }
    }

    public boolean isShopAvailableFor(Player player) {
        if (player == null) {
            return false;
        }
        if (!s.shopEnabled) {
            return false;
        }
        if (player.isAdmin()) {
            return true;
        }
        return !s.requireShopZone || (zoneService != null && zoneService.isInEnabledZone(player));
    }

    public boolean isSystemShopAvailableFor(Player player) {
        if (player == null) {
            return false;
        }
        return zoneService == null
                ? s.systemShopEnabled
                : zoneService.systemShopEnabledFor(player, s.systemShopEnabled);
    }

    public String shopUnavailableMessage(Player player) {
        if (!s.shopEnabled) {
            return t.get("TC_SHOP_DISABLED", player);
        }
        if (s.requireShopZone) {
            return t.get("TC_SHOP_ZONE_REQUIRED", player);
        }
        return t.get("TC_SHOP_NO_OFFERS", player);
    }

    public ShopZoneService shopZoneService() {
        return zoneService;
    }

    public boolean walletAvailable() {
        return service != null && service.walletAvailable();
    }

    public Optional<ShopZone> currentShopZone(Player player) {
        return zoneService == null || player == null ? Optional.empty() : zoneService.zoneAt(player);
    }

    public List<ShopZone> listShopZones() {
        return zoneService == null ? List.of() : zoneService.listZones();
    }

    public ShopZone setZoneSystemShop(long areaId, int systemShop) {
        return zoneService == null ? null : zoneService.setSystemShopMode(areaId, systemShop).orElse(null);
    }

    public boolean showShopZoneIndicator() {
        return s != null && s.showShopZoneIndicator;
    }

    public void openShopUI(Player player) {
        ShopOverlay existing = (ShopOverlay) player.getAttribute("oz.shop.ui.overlay");
        if (existing != null) {
            player.removeUIElement(existing);
            player.deleteAttribute("oz.shop.ui.overlay");
            CursorManager.hide(player);
        }
        ShopOverlay overlay = new ShopOverlay(this, player);
        CursorManager.show(player);
        player.addUIElement(overlay);
        player.setAttribute("oz.shop.ui.overlay", overlay);
    }

    public void sendOfferList(Player player) {
        List<ShopOffer> offers = listOffers().stream()
                .filter(ShopOffer::isEnabled)
                .filter(offer -> !offer.isSystemOffer() || isSystemShopAvailableFor(player))
                .toList();
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
                    + offer.getPrice(player) + " " + currency + ")");
        }
        player.sendTextMessage(c.text + t.get("TC_SHOP_USAGE", player).replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    public static ShopPurchaseResult unavailable(String message) {
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, message);
    }
}
