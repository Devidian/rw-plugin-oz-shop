package de.omegazirkel.risingworld;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.omegazirkel.risingworld.shop.PluginSettings;
import de.omegazirkel.risingworld.shop.DynamicEconomyPricing;
import de.omegazirkel.risingworld.shop.EconomyReportService;
import de.omegazirkel.risingworld.shop.ShopEconomyStore;
import de.omegazirkel.risingworld.shop.ShopErrorCode;
import de.omegazirkel.risingworld.shop.ShopItemNames;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopOfferIcons;
import de.omegazirkel.risingworld.shop.ShopOfferRegistrationResult;
import de.omegazirkel.risingworld.shop.ShopPluginInfoStatusProvider;
import de.omegazirkel.risingworld.shop.ShopPurchaseCallback;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.shop.ShopService;
import de.omegazirkel.risingworld.shop.ShopStockMode;
import de.omegazirkel.risingworld.shop.ShopZone;
import de.omegazirkel.risingworld.shop.ShopZoneService;
import de.omegazirkel.risingworld.shop.Trader;
import de.omegazirkel.risingworld.shop.TraderService;
import de.omegazirkel.risingworld.shop.TraderGeneratorConfig;
import de.omegazirkel.risingworld.shop.FactionTraderBridge;
import de.omegazirkel.risingworld.shop.SystemOfferFile;
import de.omegazirkel.risingworld.shop.SystemOfferEditor;
import de.omegazirkel.risingworld.shop.PluginGUI;
import de.omegazirkel.risingworld.shop.ShopPlayerPreferences;
import de.omegazirkel.risingworld.shop.exports.ShopZoneExportService;
import de.omegazirkel.risingworld.shop.exports.ShopTraderExportService;
import de.omegazirkel.risingworld.shop.web.ShopZoneRoute;
import de.omegazirkel.risingworld.shop.web.ShopTraderRoute;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.shop.ui.ShopOverlay;
import de.omegazirkel.risingworld.shop.ui.ShopPlayerPluginData;
import de.omegazirkel.risingworld.shop.ui.ShopPlayerPluginSettings;
import de.omegazirkel.risingworld.shop.ui.ShopZoneIndicatorProvider;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.PlayerSettings;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.Timer;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerNpcInteractionEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Npc;
import net.risingworld.api.objects.Skin;
import net.risingworld.api.World;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Npcs;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.ui.UITarget;

class ShopRuntime extends Plugin {
    private final Map<String, FactionPayoutAuthorization> factionPayouts = new ConcurrentHashMap<>();
    private record FactionPayoutAuthorization(int factionId, int playerDbId, long amount, String currency) { }

    public boolean isFactionTraderPaymentReserved(String correlationId, int factionId, int playerDbId,
            long amount, String currency) {
        FactionPayoutAuthorization authorization = factionPayouts.get(correlationId);
        return authorization != null && authorization.factionId() == factionId
                && authorization.playerDbId() == playerDbId && authorization.amount() == amount
                && authorization.currency().equalsIgnoreCase(currency);
    }

    public boolean isFactionTraderPaymentReversalAllowed(String correlationId, int factionId) {
        FactionPayoutAuthorization authorization = factionPayouts.get(correlationId);
        return authorization != null && authorization.factionId() == factionId;
    }
    static final Colors c = Colors.getInstance();
    private static I18n t;
    private static PluginSettings s;
    private static ShopService service;
    private static ShopZoneService zoneService;
    private static ShopEconomyStore economyStore;
    private static TraderService traderService;
    private static TraderGeneratorConfig traderGeneratorConfig;
    private static Map<String, Double> completeCatalogBaseUnitPrices = Map.of();
    private static final int[] LIGHT_SKIN_COLORS = { 0xFFE0BD, 0xF1C27D, 0xE0AC69, 0xFFDBAC };
    private static final int[] MEDIUM_SKIN_COLORS = { 0xC68642, 0xA56B46, 0x8D5524 };
    private static final int[] DARK_SKIN_COLORS = { 0x6F4E37, 0x4B2E20 };
    private static final int[] HAIR_COLORS = { 0x1C120C, 0x3A2518, 0x5A381E, 0x8B5A2B, 0xC48A4A, 0xD9B36C };
    private static final int[] EYE_COLORS = { 0x4E7AA8, 0x5A8E50, 0x6B4A2E, 0x7D8B44 };
    private static final int MALE_HAIRSTYLE_FIRST = 50;
    private static final int MALE_HAIRSTYLE_LAST = 68;
    private static final int FEMALE_HAIRSTYLE_FIRST = 100;
    private static final int FEMALE_HAIRSTYLE_LAST = 119;

    private record GeneratedTraderAppearance(String name, boolean male, List<String> clothing,
                                             int skinColor, int hairColor, int eyeColor,
                                             byte hairstyle, byte beard, byte variation) {
    }

    private static Connection sqliteCon;
    public static String name;
    public static PlayerSettings ps;
    private Timer economyTimer;
    private static final String WEBSERVER_ZONES_ROUTE = "zones";
    private static final String WEBSERVER_TRADERS_ROUTE = "traders";
    private ShopZoneRoute webserverZonesRoute;
    private ShopTraderRoute webserverTradersRoute;

    public static OZLogger logger() {
        return OZLogger.getInstance("OZ.Shop");
    }

    @Override
    public void onEnable() {
        name = getDescription("name");
        s = PluginSettings.getInstance((Shop) this);
        t = I18n.getInstance(this);
        s.initSettings();
        sqliteCon = SQLiteConnectionFactory.open(this);
        ps = new PlayerSettings(sqliteCon);
        service = new ShopService(new WalletBridge(this));
        zoneService = new ShopZoneService((Shop) this, sqliteCon, s.shopZonesFile);
        economyStore = new ShopEconomyStore(sqliteCon);
        traderService = new TraderService(sqliteCon);
        registerWebserverZoneRoute();
        copyDefaultTraderOffers();
        traderGeneratorConfig = loadTraderGeneratorConfig();
        reloadSystemOffers();
        initializeEconomyScopes();

        PluginGUI.getInstance((Shop) this);
        ShopOfferIcons.PreloadResult iconPreload = ShopOfferIcons.preload(service.listSystemOffers());
        logger().info("Preloaded " + iconPreload.loaded() + " system-offer icons in "
                + iconPreload.durationMillis() + " ms" + (iconPreload.failed() > 0
                        ? " (" + iconPreload.failed() + " failed)"
                        : ""));
        PluginShortcutVisibility.register(name, ShopPlayerPreferences::shortcutVisible);
        SharedIndicators.registerProvider(name, new ShopZoneIndicatorProvider((Shop) this));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(new ShopPlayerPluginSettings(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new ShopPlayerPluginData(getDescription("version")));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(
                new PlayerPluginAdminSettings(name, getDescription("version"), () -> s.adminSettingsEntries(),
                        s::initSettings));
        PluginInfoStatusProviders.registerProvider(
                new ShopPluginInfoStatusProvider((Shop) this, getDescription("version")));
        syncEconomyTimer();
        executeDelayed(1.0f, this::dissolveMissingTradersAfterEnable);
        logger().info(getName() + " Plugin is enabled version:" + getDescription("version"));
    }

    @Override
    public void onDisable() {
        if (webserverZonesRoute != null) { unregisterWebserverHandler(WEBSERVER_ZONES_ROUTE); webserverZonesRoute = null; }
        if (webserverTradersRoute != null) { unregisterWebserverHandler(WEBSERVER_TRADERS_ROUTE); webserverTradersRoute = null; }
        stopEconomyTimer();
        if (service != null) {
            service.clear();
        }
        if (name != null) {
            PluginShortcutVisibility.unregister(name);
            PluginInfoStatusProviders.unregisterProvider(name);
        }
        SharedIndicators.unregisterProvider(name);
        if (sqliteCon != null) {
            try {
                sqliteCon.close();
            } catch (SQLException ex) {
                logger().error("Failed to close shop database connection: " + ex.getMessage());
            }
        }
    }

    private void registerWebserverZoneRoute() {
        webserverZonesRoute = new ShopZoneRoute(() -> s.exposeShopZones, new ShopZoneExportService(sqliteCon));
        webserverTradersRoute = new ShopTraderRoute(() -> s.exposeShopTraders,
                new ShopTraderExportService(this::listTraders, this::listTraderSystemOffers,
                        (trader, offer) -> dynamicTraderOffer(trader, offer, 1),
                        this::traderEconomyStateFor,
                        accountId -> new WalletBridge(this).systemAccountBalances(accountId)));
        registerWebserverHandler(WEBSERVER_ZONES_ROUTE, webserverZonesRoute);
        registerWebserverHandler(WEBSERVER_TRADERS_ROUTE, webserverTradersRoute);
        logger().info("Native Shop routes registered at /" + WEBSERVER_ZONES_ROUTE + " and /" + WEBSERVER_TRADERS_ROUTE);
    }

    public void onSettingsChanged(Path settingsPath) {
        s.initSettings(settingsPath.toString());
        zoneService = new ShopZoneService((Shop) this, sqliteCon, s.shopZonesFile);
        reloadSystemOffers();
        initializeEconomyScopes();
        syncEconomyTimer();
    }

    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        ShopPlayerPreferences.load(player);
        if (s.enableWelcomeMessage) {
            player.sendTextMessage(t.get("tc.msg.plugin.welcome", player)
                    .replace("PH_PLUGIN_NAME", getDescription("name"))
                    .replace("PH_PLUGIN_CMD", s.shopCommand)
                    .replace("PH_PLUGIN_VERSION", getDescription("version")));
        }
        if (player.isAdmin() && !service.walletAvailable()) {
            player.sendTextMessage(c.warning + t.get("tc.shop.warn.wallet.missing", player));
        }
    }

    public void onPlayerUITextFieldChangeEvent(PlayerUITextFieldChangeEvent event) {
        Player player = event.getPlayer();
        Object overlay = player.getAttribute("oz.shop.ui.overlay");
        if (overlay instanceof ShopOverlay shopOverlay) {
            shopOverlay.onAmountFieldChanged(event.getUITextField(), event.getNewText());
        }
    }

    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] cmdParts = event.getCommand().split(" ", 4);
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
            player.sendTextMessage(c.okay + t.get("tc.shop.reloaded", player).replace("PH_COUNT", String.valueOf(count)));
            return;
        }
        if (cmdParts.length > 1 && (cmdParts[1].equalsIgnoreCase("maketrader") || cmdParts[1].equalsIgnoreCase("mt"))
                && player.isAdmin()) {
            makeTrader(player);
            return;
        }
        if (cmdParts.length > 1 && (cmdParts[1].equalsIgnoreCase("createtrader") || cmdParts[1].equalsIgnoreCase("ct"))
                && player.isAdmin()) {
            if (cmdParts.length == 2) createTrader(player);
            else if (cmdParts.length == 3 && cmdParts[2].equalsIgnoreCase("male")) createTrader(player, true);
            else if (cmdParts.length == 3 && cmdParts[2].equalsIgnoreCase("female")) createTrader(player, false);
            else player.sendTextMessage(c.warning + t.get("tc.shop.trader.create.gender.usage", player));
            return;
        }
        if (cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("factiontrader")) {
            if (cmdParts.length == 2) createFactionTrader(player);
            else if (cmdParts.length == 3 && cmdParts[2].equalsIgnoreCase("male")) createFactionTrader(player, true);
            else if (cmdParts.length == 3 && cmdParts[2].equalsIgnoreCase("female")) createFactionTrader(player, false);
            else player.sendTextMessage(c.warning + t.get("tc.shop.trader.faction.create.gender.usage", player));
            return;
        }
        if (cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("zoneoffers") && player.isAdmin()) {
            setCurrentZoneOfferFile(player, cmdParts.length == 3 ? cmdParts[2] : "");
            return;
        }
        if (cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("stock") && player.isAdmin()) {
            configureStock(player, cmdParts.length == 4 ? cmdParts[2] : "", cmdParts.length == 4 ? cmdParts[3] : "");
            return;
        }
        if (cmdParts.length > 1 && cmdParts[1].equalsIgnoreCase("economy") && player.isAdmin()) {
            configureEconomy(player, cmdParts.length >= 3 ? cmdParts[2] : "", cmdParts.length == 4 ? cmdParts[3] : "");
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
        if (cmdParts[1].equalsIgnoreCase("buy") && cmdParts.length >= 3) {
            ShopPurchaseResult result = purchase(player, cmdParts[2],
                    cmdParts.length == 4 ? parsePositiveInt(cmdParts[3], 1) : 1);
            if (result.success) player.sendTextMessage(c.okay + result.message);
            else player.showErrorMessageBox(t.get("tc.shop.ui.title", player), purchaseMessage(player, result));
            return;
        }
        if (cmdParts[1].equalsIgnoreCase("sell") && cmdParts.length >= 3) {
            ShopPurchaseResult result = sell(player, cmdParts[2],
                    cmdParts.length == 4 ? parsePositiveInt(cmdParts[3], 1) : 1);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            return;
        }
        player.sendTextMessage(c.warning + t.get("tc.shop.usage", player).replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    public void onPlayerNpcInteractionEvent(PlayerNpcInteractionEvent event) {
        if (event.getInteractionType() != Npcs.InteractionType.Default) return;
        Npc npc = event.getNpc();
        if (npc == null || traderService == null) return;
        Trader trader = traderService.find(npc.getGlobalID()).orElse(null);
        if (trader == null) return;
        Player player = event.getPlayer();
        player.closeAllActiveUIWindows();
        openTraderUI(player, trader);
        event.setCancelled(true);
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

    public ShopOfferRegistrationResult registerContextOffer(String id, String title, String description, long price,
            String currencyIdentifier, String icon, String category, String source, String pluginIdentifier,
            de.omegazirkel.risingworld.shop.ShopPurchaseContextCallback callback,
            de.omegazirkel.risingworld.shop.ShopPurchasePolicy policy,
            de.omegazirkel.risingworld.shop.ShopPriceResolver priceResolver) {
        return service.registerContextPluginOffer(id, title, description, price, currencyIdentifier, icon, category,
                source, pluginIdentifier, callback, policy, priceResolver);
    }
    public ShopOfferRegistrationResult registerContextOffer(String id, String title, String description, long price,
            String currencyIdentifier, String icon, String category, String source, String pluginIdentifier,
            de.omegazirkel.risingworld.shop.ShopPurchaseContextCallback callback,
            de.omegazirkel.risingworld.shop.ShopPurchasePolicy policy,
            de.omegazirkel.risingworld.shop.ShopPriceResolver priceResolver,
            de.omegazirkel.risingworld.shop.ShopOfferLocalization localization) {
        return service.registerContextPluginOffer(id, title, description, price, currencyIdentifier, icon, category,
                source, pluginIdentifier, callback, policy, priceResolver, localization);
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
            String id, String title, String description, long price, String currencyIdentifier, String icon,
            String pluginIdentifier, ShopPurchaseCallback callback,
            de.omegazirkel.risingworld.shop.ShopOfferLocalization localization) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, "",
                pluginIdentifier, pluginIdentifier, callback, null, localization);
    }

    public ShopOfferRegistrationResult registerOffer(
            String id, String title, String description, long price, String currencyIdentifier, String icon,
            String pluginIdentifier, ShopPurchaseCallback callback,
            de.omegazirkel.risingworld.shop.ShopPriceResolver priceResolver,
            de.omegazirkel.risingworld.shop.ShopOfferLocalization localization) {
        return service.registerPluginOffer(id, title, description, price, currencyIdentifier, icon, "",
                pluginIdentifier, pluginIdentifier, callback, priceResolver, localization);
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
        return purchase(player, offerId, 1);
    }

    /** Converts Wallet/framework failures into player-facing, localized Shop messages. */
    public String purchaseMessage(Player player, ShopPurchaseResult result) {
        if (result.success) return result.message;
        if (result.errorCode == ShopErrorCode.PAYMENT_FAILED) {
            if (result.message != null && result.message.toLowerCase(Locale.ROOT).contains("balance is too low"))
                return t.get("tc.shop.purchase.error.insufficient_balance", player);
            return t.get("tc.shop.purchase.error.payment_failed", player);
        }
        if (result.errorCode == ShopErrorCode.REFUND_FAILED)
            return t.get("tc.shop.purchase.error.refund_failed", player);
        if (result.errorCode == ShopErrorCode.CALLBACK_FAILED
                && result.message != null && result.message.contains("Payment was refunded."))
            return t.get("tc.shop.purchase.error.refunded", player);
        return result.message;
    }

    public ShopPurchaseResult purchase(Player player, String offerId, int quantity) {
        return purchase(player, offerId, quantity, false);
    }

    public ShopPurchaseResult purchasePluginOfferAtTrader(Player player, Trader trader, String offerId, int quantity) {
        if (trader == null || traderService == null || traderService.find(trader.npcId())
                .filter(Trader::pluginShopEnabled).isEmpty()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.plugin.disabled", player));
        }
        ShopOffer offer = findOffer(offerId);
        if (offer == null || offer.isSystemOffer()) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, t.get("tc.shop.no.offers", player));
        }
        return purchase(player, offerId, quantity, true);
    }

    private ShopPurchaseResult purchase(Player player, String offerId, int quantity, boolean traderPluginShop) {
        ShopOffer offer = traderPluginShop ? findOffer(offerId)
                : findSystemOfferFor(player, offerId).orElseGet(() -> findOffer(offerId));
        if (offer != null && offer.isSystemOffer() && !isSystemShopAvailableFor(player)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.system.disabled", player));
        }
        if (offer != null && !offer.isSystemOffer() && !traderPluginShop && !isPluginShopAvailableFor(player)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.plugin.disabled", player));
        }
        int effectiveQuantity = Math.max(1, quantity);
        String economyScope = ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null));
        ShopOffer effectiveOffer = dynamicEconomyOffer(player, offer, effectiveQuantity);
        if (offer != null && offer.isSystemOffer() && economyStore != null
                && !economyStore.canSellToPlayer(economyScope, effectiveOffer)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED,
                    t.get("tc.shop.dynamic.stock.empty", player));
        }
        ShopPurchaseResult result = offer == null
                ? service.purchase(player, offerId)
                : offer.isSystemOffer()
                        ? service.purchase(player, effectiveOffer, 1)
                        : service.purchase(player, offer, effectiveQuantity);
        if (result.success && result.offer != null && result.offer.isSystemOffer() && economyStore != null) {
            long price = 0L;
            try {
                price = result.offer.getPrice(player);
            } catch (RuntimeException ex) {
                logger().warn("Could not record shop sale value for " + result.offer.getId() + ": " + ex.getMessage());
            }
            economyStore.recordSystemSale(economyScope, result.offer, price);
        }
        return localizedSystemTransactionResult(player, result, "tc.shop.purchase.completed");
    }

    public ShopPurchaseResult sell(Player player, String offerId, int quantity) {
        return sell(player, offerId, quantity, null);
    }

    public ShopPurchaseResult sell(Player player, String offerId, int quantity, ShopService.SellQuote selectedQuote) {
        ShopOffer offer = findSystemOfferFor(player, offerId).orElse(null);
        if (offer != null && !isSystemShopAvailableFor(player)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.system.disabled", player));
        }
        int effectiveQuantity = Math.max(1, quantity);
        ShopOffer effectiveOffer = dynamicEconomyOffer(player, offer, effectiveQuantity);
        if (selectedQuote != null && selectedQuote.amount() != effectiveOffer.getAmount()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                    t.get("tc.shop.sale.selection.amount", player));
        }
        if (offer != null && economyStore != null) {
            ShopEconomyStore.EconomyCheck check = economyStore.canBuyFromPlayer(
                    ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)), player.getDbID(), effectiveOffer);
            if (!check.allowed()) {
                String key = check.messageKey().isBlank() ? "tc.shop.stock.update.failed" : check.messageKey();
                return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get(key, player));
            }
        }
        ShopPurchaseResult result = selectedQuote == null ? service.sell(player, effectiveOffer, 1)
                : service.sell(player, effectiveOffer, selectedQuote);
        if (result.success && result.offer != null && result.offer.isSystemOffer() && economyStore != null) {
            economyStore.recordSystemBuy(ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)),
                    player.getDbID(),
                    result.offer, result.offer.getBuyPrice());
        }
        return localizedSystemTransactionResult(player, result, "tc.shop.sale.completed");
    }

    public List<Trader> listTraders() {
        return traderService == null ? List.of() : traderService.list();
    }

    public int getFactionTraderCount(int factionId) {
        return traderService == null ? -1 : traderService.factionTraderCount(factionId);
    }

    public boolean canManageTrader(Player player, Trader trader) {
        if (player == null || trader == null || traderService == null) return false;
        Trader persisted = traderService.find(trader.npcId()).orElse(null);
        if (persisted == null || persisted.factionId() != trader.factionId()
                || !persisted.systemOffersFile().equals(trader.systemOffersFile())) return false;
        if (trader.factionId() == 0) return player.isAdmin();
        FactionTraderBridge factions = new FactionTraderBridge(this);
        Integer actorFaction = factions.factionIdFor(player.getDbID());
        return actorFaction != null && actorFaction == trader.factionId() && factions.canManage(player.getDbID());
    }

    public List<ShopOffer> listTraderSystemOffers(Trader trader) {
        if (trader == null) return List.of();
        String file = trader.systemOffersFile().isBlank() ? "default-trader.json" : trader.systemOffersFile();
        List<ShopOffer> offers = SystemOfferFile.load((Shop) this, file, s.generateDefinitionExports, s.systemShopCurrency);
        if (trader.factionId() == 0) return offers;
        return offers.stream().filter(offer -> offer.getStockMode() == ShopStockMode.PLAYER_SUPPLIED
                && offer.getDrainPercent() == 0.0d && offer.getDrainMax() == 0L && offer.getRestockMax() == 0L
                && offer.getDefaultDrainRate() == 0.0d && offer.getDefaultRefillRate() == 0.0d).toList();
    }

    public synchronized ShopPurchaseResult traderPurchase(Player player, Trader trader, String offerId, int quantity) {
        if (player == null || player.getDbID() <= 0)
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "A valid player is required.");
        trader = trader == null || traderService == null ? null : traderService.find(trader.npcId()).orElse(null);
        if (trader == null) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Trader not found.");
        ShopOffer offer = findTraderOffer(trader, offerId).orElse(null);
        if (offer == null) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Trader offer not found.");
        if (trader.factionId() > 0) {
            if (quantity <= 0 || (long) quantity * offer.getAmount() > Integer.MAX_VALUE
                    || !offer.canPlayerBuyFromSystem())
                return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "Invalid faction trader purchase amount.");
            FactionTraderBridge factions = new FactionTraderBridge(this);
            String accountId = factions.accountId(trader.factionId());
            WalletBridge wallet = new WalletBridge((Shop) this);
            if (economyStore == null || !wallet.isAvailable() || !wallet.hasSystemAccountApi()
                    || accountId == null || accountId.isBlank() || !economyStore.hasFactionState(trader.economyScope(), offer.getId()))
                return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, "Faction trader data is unavailable.");
            ShopOffer factionOffer = dynamicTraderOffer(trader, offer, quantity);
            String operationId = "shop:faction-trader:" + trader.factionId() + ":out:" + UUID.randomUUID();
            if (!economyStore.reserveFactionStock(trader.economyScope(), factionOffer, "OUT", operationId))
                return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.trader.dynamic.stock.empty", player));
            ShopPurchaseResult result;
            try {
                result = service.purchaseFromSystemAccount(player, factionOffer, 1, accountId);
            } catch (RuntimeException ex) {
                logger().error("Faction trader purchase outcome requires reconciliation: " + operationId + ": " + ex.getMessage());
                return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, "Faction trader trade requires reconciliation.");
            }
            if (!economyStore.finishFactionStock(operationId, result.success))
                logger().error("Faction trader purchase stock compensation/finalization pending: " + operationId);
            return localizedSystemTransactionResult(player, result, "tc.shop.purchase.completed");
        }
        ShopOffer effective = dynamicTraderOffer(trader, offer, quantity);
        if (economyStore != null && !economyStore.canSellToPlayer(trader.economyScope(), effective)) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.trader.dynamic.stock.empty", player));
        }
        ShopPurchaseResult result = service.purchaseFromSystemAccount(player, effective, 1, trader.accountId());
        if (result.success && economyStore != null) economyStore.recordSystemSale(trader.economyScope(), effective,
                effective.getPrice(player));
        return localizedSystemTransactionResult(player, result, "tc.shop.purchase.completed");
    }

    public ShopPurchaseResult traderSell(Player player, Trader trader, String offerId, int quantity) {
        return traderSell(player, trader, offerId, quantity, null);
    }

    public synchronized ShopPurchaseResult traderSell(Player player, Trader trader, String offerId, int quantity,
            ShopService.SellQuote selectedQuote) {
        if (player == null || player.getDbID() <= 0)
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "A valid player is required.");
        trader = trader == null || traderService == null ? null : traderService.find(trader.npcId()).orElse(null);
        if (trader == null) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Trader not found.");
        ShopOffer offer = findTraderOffer(trader, offerId).orElse(null);
        if (offer == null) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, "Trader offer not found.");
        if (trader.factionId() > 0) {
            if (quantity <= 0 || (long) quantity * offer.getAmount() > Integer.MAX_VALUE
                    || !offer.canPlayerSellToSystem())
                return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, "Invalid faction trader sale amount.");
            FactionTraderBridge factions = new FactionTraderBridge(this);
            WalletBridge wallet = new WalletBridge((Shop) this);
            String accountId = factions.accountId(trader.factionId());
            if (economyStore == null || !wallet.isAvailable() || !wallet.hasSystemAccountApi()
                    || accountId == null || accountId.isBlank() || !economyStore.hasFactionState(trader.economyScope(), offer.getId()))
                return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, "Faction trader data is unavailable.");
            ShopOffer factionOffer = dynamicTraderOffer(trader, offer, quantity);
            ShopEconomyStore.EconomyCheck stockCheck = economyStore.canBuyFromPlayer(
                    trader.economyScope(), player.getDbID(), factionOffer);
            if (!stockCheck.allowed()) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED,
                    t.get(stockCheck.messageKey(), player));
            ShopService.SellQuote quote = selectedQuote == null ? service.quoteSell(player, factionOffer) : selectedQuote;
            if (!quote.sellable() || quote.amount() != factionOffer.getAmount())
                return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, quote.message());
            String currency = factionOffer.getCurrencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                    : factionOffer.getCurrencyIdentifier();
            if (currency == null || currency.isBlank() || !TraderService.hasSufficientBasePayoutBalance(
                    traderBalance(wallet, trader, currency), quote.traderPayoutCap()))
                return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED,
                        t.get("tc.shop.trader.insufficient.balance", player));
            String operationId = "shop:faction-trader:" + trader.factionId() + ":in:" + UUID.randomUUID();
            if (!economyStore.reserveFactionStock(trader.economyScope(), factionOffer, "IN", operationId))
                return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED, t.get("tc.shop.trader.dynamic.stock.full", player));
            factionPayouts.put(operationId, new FactionPayoutAuthorization(trader.factionId(),
                    player.getDbID(), quote.traderPayoutCap(), currency));
            ShopPurchaseResult result;
            try {
                result = service.sellToFactionAccount(player, factionOffer, quote,
                        trader.factionId(), factions, operationId);
            } catch (RuntimeException ex) {
                logger().error("Faction trader sale outcome requires reconciliation: " + operationId + ": " + ex.getMessage());
                return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, "Faction trader trade requires reconciliation.");
            } finally { factionPayouts.remove(operationId); }
            if (!economyStore.finishFactionStock(operationId, result.success))
                logger().error("Faction trader sale stock compensation/finalization pending: " + operationId);
            return localizedSystemTransactionResult(player, result, "tc.shop.sale.completed");
        }
        ShopOffer effective = dynamicTraderOffer(trader, offer, quantity);
        if (selectedQuote != null && selectedQuote.amount() != effective.getAmount()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT,
                    t.get("tc.shop.sale.selection.amount", player));
        }
        if (economyStore != null) {
            ShopEconomyStore.EconomyCheck check = economyStore.canBuyFromPlayer(trader.economyScope(), player.getDbID(), effective);
            if (!check.allowed()) return ShopPurchaseResult.failure(ShopErrorCode.OFFER_DISABLED,
                    t.get(check.messageKey(), player));
        }
        ShopService.SellQuote quote = selectedQuote == null ? service.quoteSell(player, effective) : selectedQuote;
        if (!quote.sellable()) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, quote.message());
        WalletBridge wallet = new WalletBridge((Shop) this);
        String currency = effective.getCurrencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                : effective.getCurrencyIdentifier();
        String premiumCorrelation = "trader:" + trader.npcId() + ":modifier-premium:" + UUID.randomUUID();
        long premium = quote.worldModifierPremium();
        long traderBalance = traderBalance(wallet, trader, currency);
        if (!TraderService.hasSufficientBasePayoutBalance(traderBalance, quote.traderPayoutCap())) {
            return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED,
                    t.get("tc.shop.trader.insufficient.balance", player));
        }
        if (premium > 0L) {
            WalletBridge.WalletTransferCallResult funding = wallet.transferWorldToSystemIdempotent(trader.accountId(), premium,
                    "Trader modifier premium: " + effective.getId(), currency, "OZ - Shop", premiumCorrelation);
            if (!funding.success()) return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, funding.message());
        }
        ShopPurchaseResult result = selectedQuote == null ? service.sellToSystemAccount(player, effective, 1, trader.accountId())
                : service.sellToSystemAccount(player, effective, selectedQuote, trader.accountId());
        if (!result.success && premium > 0L) {
            wallet.reverseAccountTransferIdempotent(premiumCorrelation, premiumCorrelation + ":rollback",
                    "Trader modifier premium rollback: " + effective.getId(), "OZ - Shop");
        }
        if (result.success && economyStore != null) economyStore.recordSystemBuy(trader.economyScope(), player.getDbID(),
                effective, effective.getBuyPrice());
        return localizedSystemTransactionResult(player, result, "tc.shop.sale.completed");
    }

    public long traderBalance(Trader trader) {
        if (trader == null) return 0L;
        String currency = s.systemShopCurrency.isBlank() ? new WalletBridge((Shop) this).defaultCurrencyIdentifier()
                : s.systemShopCurrency;
        return traderBalance(new WalletBridge((Shop) this), trader, currency);
    }

    private long traderBalance(WalletBridge wallet, Trader trader, String currency) {
        if (wallet == null || trader == null || currency == null || currency.isBlank()) return 0L;
        String accountId = trader.factionId() > 0 ? new FactionTraderBridge(this).accountId(trader.factionId())
                : trader.accountId();
        if (accountId == null || accountId.isBlank()) return 0L;
        return wallet.systemAccountBalances(accountId).stream()
                .filter(balance -> balance.currencyIdentifier().equalsIgnoreCase(currency)).mapToLong(WalletBridge.SystemBalanceInfo::balance)
                .findFirst().orElse(0L);
    }

    /** Restores one trader's start capital from the Wallet world account. */
    public ShopPurchaseResult replenishTraderStartCapital(Player player, Trader trader) {
        if (player == null || !player.isAdmin() || trader == null || trader.factionId() > 0) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.trader.replenish.failed", player));
        }
        WalletBridge wallet = new WalletBridge((Shop) this);
        String currency = s.systemShopCurrency.isBlank() ? wallet.defaultCurrencyIdentifier() : s.systemShopCurrency;
        if (currency.isBlank() || wallet.worldSystemAccountId().isBlank()) {
            return ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, t.get("tc.shop.trader.replenish.failed", player));
        }
        WalletBridge.WalletTransferCallResult transfer = wallet.transferWorldToSystemIdempotent(trader.accountId(),
                1000L, "Trader replenishment", currency,
                "OZ - Shop", "trader:" + trader.npcId() + ":replenish:" + UUID.randomUUID());
        return transfer.success()
                ? ShopPurchaseResult.success(t.get("tc.shop.trader.replenished", player)
                        .replace("PH_TRADER", trader.name()).replace("PH_AMOUNT", "1000"), null)
                : ShopPurchaseResult.failure(ShopErrorCode.PAYMENT_FAILED, t.get("tc.shop.trader.replenish.failed", player));
    }

    public String traderBuyDisabledReason(Player player, Trader trader, ShopOffer offer, int quantity) {
        if (trader == null || offer == null || !offer.isSystemOffer() || !offer.canPlayerBuyFromSystem() || economyStore == null) return "";
        if (trader.factionId() > 0 && (!economyStore.hasFactionState(trader.economyScope(), offer.getId())
                || economyStore.stateForWithoutTick(trader.economyScope(), offer).stock()
                        < (long) Math.max(1, quantity) * offer.getAmount()))
            return t.get("tc.shop.trader.dynamic.stock.empty", player);
        ShopOffer effective = dynamicTraderOffer(trader, offer, quantity);
        return economyStore.canSellToPlayer(trader.economyScope(), effective) ? ""
                : t.get("tc.shop.trader.dynamic.stock.empty", player);
    }

    public String traderSellDisabledReason(Player player, Trader trader, ShopOffer offer, int quantity) {
        if (trader == null || offer == null || !offer.isSystemOffer() || !offer.canPlayerSellToSystem() || economyStore == null) return "";
        ShopOffer effective = dynamicTraderOffer(trader, offer, quantity);
        ShopEconomyStore.EconomyCheck check = economyStore.canBuyFromPlayer(trader.economyScope(), player.getDbID(), effective);
        if (!check.allowed()) {
            String key = "tc.shop.dynamic.stock.full".equals(check.messageKey())
                    ? "tc.shop.trader.dynamic.stock.full" : check.messageKey();
            return t.get(key, player);
        }
        ShopService.SellQuote quote = service.quoteSell(player, effective);
        if (!quote.sellable()) return "";
        WalletBridge wallet = new WalletBridge((Shop) this);
        String currency = effective.getCurrencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                : effective.getCurrencyIdentifier();
        return TraderService.hasSufficientBasePayoutBalance(traderBalance(wallet, trader, currency), quote.traderPayoutCap())
                ? "" : t.get("tc.shop.trader.insufficient.balance", player);
    }

    public Trader renameTrader(long npcId, String name) {
        Trader existing = traderService == null ? null : traderService.find(npcId).orElse(null);
        return existing == null || existing.factionId() > 0 ? null : renameTraderInternal(npcId, name);
    }

    public Trader renameTrader(Player actor, long npcId, String name) {
        Trader existing = traderService == null ? null : traderService.find(npcId).orElse(null);
        return !canManageTrader(actor, existing) ? null : renameTraderInternal(npcId, name);
    }

    private Trader renameTraderInternal(long npcId, String name) {
        Trader old = traderService == null ? null : traderService.find(npcId).orElse(null);
        if (old == null || name == null || name.isBlank()) return null;
        String normalized = name.trim();
        SystemOfferEditor editor = new SystemOfferEditor((Shop) this);
        String newFile = null;
        if (old.factionId() > 0) {
            FactionTraderBridge factions = new FactionTraderBridge(this);
            String factionName = factions.factionName(old.factionId());
            if (factionName == null) return null;
            newFile = SystemOfferEditor.factionFileName(factionName, normalized);
        }
        Trader updated = newFile == null ? traderService.rename(npcId, normalized).orElse(null)
                : traderService.renameFaction(npcId, normalized, newFile, editor).orElse(null);
        if (updated != null) {
            Npc npc = World.getNpc(npcId);
            if (npc != null) npc.setName(updated.name());
            if (updated.factionId() == 0) new WalletBridge((Shop) this).updateSystemAccountDisplayName(updated.accountId(), updated.name(), "OZ - Shop");
        }
        return updated;
    }

    public Trader setTraderOffersFile(long npcId, String file) {
        Trader trader = traderService == null ? null : traderService.find(npcId).orElse(null);
        return trader == null || trader.factionId() > 0 ? null : traderService.setSystemOffersFile(npcId, file).orElse(null);
    }

    public boolean offerFileExists(String file) { return new SystemOfferEditor((Shop) this).exists(file); }
    public boolean createOfferFile(String file) { return new SystemOfferEditor((Shop) this).createEmpty(file); }
    public String selectedSystemOfferFile(Player player) { return systemOffersFileFor(player); }
    public boolean canEditOfferFile(Player player, Trader trader) { return editorOfferFile(player, trader) != null; }

    public ShopPurchaseResult addOfferFromCatalog(Player player, Trader trader, String itemName, int itemVariant) {
        if (player == null || trader == null && !player.isAdmin() || trader != null && !canManageTrader(player, trader))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.not.allowed", player));
        String file = editorOfferFile(player, trader);
        if (file == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.default.file", player));
        SystemOfferEditor.AddResult result = new SystemOfferEditor((Shop) this).addFromCatalog(file, itemName, itemVariant);
        if (result == SystemOfferEditor.AddResult.ADDED) {
            if (trader == null) reloadSystemOffers();
            else if (trader.factionId() > 0 && economyStore != null) {
                listTraderSystemOffers(trader).stream().filter(offer -> offer.getItemName().equalsIgnoreCase(itemName)
                        && offer.getItemVariant() == itemVariant).findFirst()
                        .ifPresent(offer -> economyStore.initializeFactionOffer(trader.economyScope(), offer));
            }
            return ShopPurchaseResult.success(t.get("tc.shop.editor.added", player), null);
        }
        String key = result == SystemOfferEditor.AddResult.TARGET_READ_ONLY
                ? "tc.shop.editor.file.read.only" : "tc.shop.editor.add.failed";
        return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get(key, player));
    }

    public ShopPurchaseResult removeOffer(Player player, Trader trader, ShopOffer offer) {
        if (player == null || offer == null || trader == null && !player.isAdmin()
                || trader != null && !canManageTrader(player, trader))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.not.allowed", player));
        String file = editorOfferFile(player, trader);
        if (file == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.default.file", player));
        String scope = trader == null ? ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)) : trader.economyScope();
        if (economyStore == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.remove.failed", player));
        if (trader != null && trader.factionId() > 0 && economyStore.hasPendingFactionOperations(scope, offer.getId()))
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, "Faction trader trade is still pending.");
        String removalId = new SystemOfferEditor((Shop) this).prepareRemoval(file, offer.getId());
        if (removalId == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.remove.failed", player));
        if (trader != null && trader.factionId() == 0 && !settleTraderOfferStock(trader, offer, removalId))
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, t.get("tc.shop.editor.remove.failed", player));
        if (trader != null && trader.factionId() > 0) {
            boolean removed = new SystemOfferEditor((Shop) this).removeFactionOffer(file, offer.getId(), economyStore, scope);
            return removed ? ShopPurchaseResult.success(t.get("tc.shop.editor.removed", player), null)
                    : ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.remove.failed", player));
        }
        if (!new SystemOfferEditor((Shop) this).remove(file, offer.getId()) || economyStore == null || !economyStore.deleteOffer(scope, offer.getId()))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.remove.failed", player));
        if (trader == null) reloadSystemOffers();
        return ShopPurchaseResult.success(t.get("tc.shop.editor.removed", player), null);
    }

    public ShopPurchaseResult updateOffer(Player player, Trader trader, ShopOffer offer, java.util.Map<String, Object> values) {
        if (player == null || offer == null || trader == null && !player.isAdmin()
                || trader != null && !canManageTrader(player, trader))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.not.allowed", player));
        if (trader != null && trader.factionId() > 0 && values != null && values.keySet().stream().anyMatch(
                key -> java.util.Set.of("stock", "targetStock", "stockMode", "drainRate",
                        "refillRate", "drainPercent", "drainMax", "restockPercent", "restockMax").contains(key)))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.not.allowed", player));
        String file = editorOfferFile(player, trader);
        if (file == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.default.file", player));
        boolean updated = new SystemOfferEditor((Shop) this).update(file, offer.getId(), values);
        if (updated && values != null && values.get("stock") instanceof Number stock && economyStore != null) {
            String scope = trader == null ? ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)) : trader.economyScope();
            updated = economyStore.configure(scope, offer.getId(), Math.max(0L, stock.longValue()),
                    offer.getDefaultDrainRate(), offer.getDefaultRefillRate());
        }
        if (updated && trader == null) {
            // Global offers are cached by ShopService. Reload before the overlay
            // rebuilds so stock targets and dynamic card prices use the saved data.
            reloadSystemOffers();
        }
        return updated ? ShopPurchaseResult.success(t.get("tc.shop.editor.saved", player), null)
                : ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.save.failed", player));
    }

    public ShopPurchaseResult moveOffer(Player player, Trader trader, ShopOffer offer, int direction) {
        if (player == null || offer == null || trader == null && !player.isAdmin()
                || trader != null && !canManageTrader(player, trader))
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.not.allowed", player));
        String file = editorOfferFile(player, trader);
        if (file == null) return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.default.file", player));
        boolean moved = new SystemOfferEditor((Shop) this).move(file, offer.getId(), direction);
        if (moved && trader == null) reloadSystemOffers();
        return moved ? ShopPurchaseResult.success(t.get("tc.shop.editor.order.saved", player), null)
                : ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.editor.order.boundary", player));
    }

    private String editorOfferFile(Player player, Trader trader) {
        if (player == null || trader == null && !player.isAdmin() || trader != null && !canManageTrader(player, trader))
            return null;
        String file = trader == null ? systemOffersFileFor(player) : trader.systemOffersFile();
        return file == null || file.isBlank() || file.equalsIgnoreCase("system-offers.default.json")
                || file.equalsIgnoreCase("default-trader.json") || file.equalsIgnoreCase(s.systemOffersFile) ? null : file;
    }

    private boolean settleTraderOfferStock(Trader trader, ShopOffer offer, String removalId) {
        if (economyStore == null) return false;
        ShopEconomyStore.EconomyState state = economyStore.stateForWithoutTick(trader.economyScope(), offer);
        long value = DynamicEconomyPricing.outboundValue(offer, state, state.stock(), s.dynamicEconomyEnabled);
        if (value <= 0L) return true;
        WalletBridge wallet = new WalletBridge((Shop) this);
        String currency = offer.getCurrencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                : offer.getCurrencyIdentifier();
        WalletBridge.WalletTransferCallResult result = wallet.transferWorldToSystemIdempotent(trader.accountId(), value,
                "Trader offer removal stock sale: " + offer.getId(), currency, "OZ - Shop",
                "trader:" + trader.npcId() + ":offer-remove:" + removalId);
        if (!result.success()) logger().error("Trader offer removal settlement failed: " + result.message());
        return result.success();
    }

    public Trader setTraderPluginShopEnabled(long npcId, boolean enabled) {
        Trader trader = traderService == null ? null : traderService.find(npcId).orElse(null);
        return trader == null || trader.factionId() > 0 ? null : traderService.setPluginShopEnabled(npcId, enabled).orElse(null);
    }

    /** Settles stock and balances before removing Shop ownership of a trader NPC. */
    public ShopPurchaseResult dissolveTrader(Player player, Trader trader, boolean deleteNpc) {
        if (player == null || trader == null || !canManageTrader(player, trader)
                || traderService == null || economyStore == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, t.get("tc.shop.trader.dissolve.failed", player));
        }
        String failure = dissolveTraderData(trader);
        if (failure != null) {
            logger().error("Could not dissolve trader '" + trader.name() + "' (NPC " + trader.npcId() + "): " + failure);
            return traderDissolveFailure(player, failure);
        }
        if (deleteNpc) {
            Npc npc = World.getNpc(trader.npcId());
            if (npc != null && !npc.isDead()) npc.delete();
        }
        return ShopPurchaseResult.success(t.get("tc.shop.trader.dissolved", player)
                .replace("PH_TRADER", trader.name()), null);
    }

    /** Cleans up only Shop-owned traders whose NPC no longer exists after a plugin reload. */
    private void dissolveMissingTradersAfterEnable() {
        if (traderService == null || economyStore == null) return;
        for (Trader trader : traderService.list()) {
            Npc npc = World.getNpc(trader.npcId());
            if (npc != null && !npc.isDead()) {
                traderService.applyNpcFlags(npc);
                byte[] clothing = traderService.clothing(trader.npcId());
                if (clothing != null) for (float delay : new float[] { .1f, .5f, 1.0f })
                    executeDelayed(delay, () -> {
                        if (!npc.isDead() && npc.getClothes() != null) npc.getClothes().deserialize(clothing);
                    });
                continue;
            }
            String failure = dissolveTraderData(trader);
            if (failure == null) {
                logger().info("Dissolved missing trader '" + trader.name() + "' (NPC " + trader.npcId() + ")");
            } else {
                logger().error("Could not dissolve missing trader '" + trader.name() + "' (NPC " + trader.npcId()
                        + "): " + failure);
            }
        }
    }

    /** Returns a failure detail, or {@code null} after a complete, idempotent trader settlement. */
    private synchronized String dissolveTraderData(Trader trader) {
        if (trader == null || traderService == null || economyStore == null) return "Trader services are unavailable.";
        if (trader.factionId() > 0) {
            if (economyStore.hasPendingFactionOperations(trader.economyScope(), null))
                return "Faction trader trades are still pending.";
            try {
                if (!sqliteCon.getAutoCommit()) return "Trader database is busy.";
                sqliteCon.setAutoCommit(false);
                try {
                    if (!traderService.delete(trader.npcId()) || !economyStore.deleteScope(trader.economyScope())) {
                        sqliteCon.rollback();
                        return "Faction trader persistence cleanup failed.";
                    }
                    sqliteCon.commit();
                } catch (Exception ex) { sqliteCon.rollback(); throw ex; }
                finally { sqliteCon.setAutoCommit(true); }
            } catch (Exception ex) {
                logger().error("Faction trader dissolution database transaction failed: " + ex.getMessage());
                return "Faction trader persistence cleanup failed.";
            }
            if (!new SystemOfferEditor((Shop) this).deleteFactionFile(trader.systemOffersFile()))
                logger().error("Faction trader offer file needs manual cleanup: " + trader.systemOffersFile());
            return null;
        }
        WalletBridge wallet = new WalletBridge((Shop) this);
        String worldAccount = wallet.worldSystemAccountId();
        if (!wallet.hasSystemAccountApi() || worldAccount.isBlank()) {
            return "Wallet system account API is unavailable.";
        }
        String prefix = "trader:" + trader.npcId() + ":dissolve";
        for (ShopOffer offer : listTraderSystemOffers(trader)) {
            ShopEconomyStore.EconomyState state = economyStore.stateForWithoutTick(trader.economyScope(), offer);
            long value = DynamicEconomyPricing.outboundValue(offer, state, state.stock(), s.dynamicEconomyEnabled);
            if (value <= 0L) continue;
            String currency = offer.getCurrencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                    : offer.getCurrencyIdentifier();
            WalletBridge.WalletTransferCallResult sale = wallet.creditSystemAccountIdempotent(trader.accountId(), value,
                    "Trader dissolution stock sale: " + offer.getId(), currency, "OZ - Shop",
                    prefix + ":stock:" + currency + ":" + offer.getId());
            if (!sale.success()) return failureDetail(sale.message(), "Trader stock settlement failed.");
        }
        for (WalletBridge.SystemBalanceInfo balance : wallet.systemAccountBalances(trader.accountId())) {
            if (balance.balance() <= 0L) continue;
            String currency = balance.currencyIdentifier().isBlank() ? wallet.defaultCurrencyIdentifier()
                    : balance.currencyIdentifier();
            WalletBridge.WalletTransferCallResult transfer = wallet.transferSystemToSystemIdempotent(trader.accountId(),
                    worldAccount, balance.balance(), "Trader dissolution: " + trader.name(), currency,
                    "OZ - Shop", prefix + ":balance:" + currency);
            if (!transfer.success()) return failureDetail(transfer.message(), "Trader balance transfer failed.");
        }
        WalletBridge.SystemAccountCallResult archive = wallet.archiveSystemAccount(trader.accountId(), "OZ - Shop");
        if (!archive.success()) return failureDetail(archive.message(), "Trader account archival failed.");
        if (!traderService.delete(trader.npcId()) || !economyStore.deleteScope(trader.economyScope())) {
            return "Trader persistence cleanup failed.";
        }
        return null;
    }

    private static String failureDetail(String detail, String fallback) {
        return detail == null || detail.isBlank() ? fallback : detail;
    }

    private ShopPurchaseResult traderDissolveFailure(Player player, String detail) {
        String message = t.get("tc.shop.trader.dissolve.failed", player);
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED,
                detail == null || detail.isBlank() ? message : message + " " + detail);
    }

    private ShopPurchaseResult localizedSystemTransactionResult(Player player, ShopPurchaseResult result, String key) {
        if (!result.success || result.offer == null || !result.offer.isSystemOffer()) {
            return result;
        }
        ShopOffer offer = result.offer;
        return ShopPurchaseResult.success(t.get(key, player)
                .replace("PH_AMOUNT", String.valueOf(offer.getAmount()))
                .replace("PH_OFFER", ShopItemNames.label(offer.getItemName(), offer.getItemVariant(),
                        offer.getTitle(player), player.getLanguage())), offer);
    }

    public ShopService.SellQuote sellQuote(Player player, ShopOffer offer, int quantity) {
        if (offer == null || !offer.isSystemOffer()) return ShopService.SellQuote.invalid("System offer not found.");
        return service.quoteSell(player, dynamicEconomyOffer(player, offer, Math.max(1, quantity)));
    }

    public ShopService.SellQuote traderSellQuote(Player player, Trader trader, ShopOffer offer, int quantity) {
        if (trader == null || offer == null || !offer.isSystemOffer()) {
            return ShopService.SellQuote.invalid("Trader offer not found.");
        }
        return service.quoteSell(player, dynamicTraderOffer(trader, offer, Math.max(1, quantity)));
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
    public List<ShopOffer> listPluginOffers(net.risingworld.api.objects.Player player) {
        return service.listPluginOffers(player);
    }

    public List<ShopOffer> listSystemOffers() {
        return service.listSystemOffers();
    }

    public List<ShopOffer> listSystemOffers(Player player) {
        if (player == null) {
            return listSystemOffers();
        }
        String offerFile = systemOffersFileFor(player);
        if (offerFile.equals(s.systemOffersFile)) {
            return listSystemOffers();
        }
        return SystemOfferFile.load((Shop) this, offerFile, s.generateDefinitionExports, s.systemShopCurrency);
    }

    /** Returns the immutable complete-catalog base price for one concrete item unit, or zero when unpriced. */
    public double systemOfferBaseUnitPrice(String itemName, int itemVariant) {
        if (itemName == null || itemName.isBlank()) return 0.0d;
        return completeCatalogBaseUnitPrices.getOrDefault(completeCatalogKey(itemName, itemVariant), 0.0d);
    }

    public ShopEconomyStore.EconomyState economyStateFor(Player player, ShopOffer offer) {
        if (offer == null || economyStore == null) {
            return new ShopEconomyStore.EconomyState(
                    offer == null ? 0L : offer.getDefaultStock(),
                    offer == null ? 0L : offer.getDefaultTargetStock(),
                    offer == null ? 0L : offer.getDefaultStockLimit(),
                    offer == null ? 0.0d : offer.getDefaultDrainRate(),
                    offer == null ? 0.0d : offer.getDefaultRefillRate());
        }
        String scope = ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null));
        return economyStore.stateFor(scope, offer);
    }

    /** Returns persistent stock scoped to one trader, never to the player's current shop zone. */
    public ShopEconomyStore.EconomyState traderEconomyStateFor(Trader trader, ShopOffer offer) {
        if (offer == null || economyStore == null || trader == null) {
            return new ShopEconomyStore.EconomyState(
                    offer == null ? 0L : offer.getDefaultStock(),
                    offer == null ? 0L : offer.getDefaultTargetStock(),
                    offer == null ? 0L : offer.getDefaultStockLimit(),
                    offer == null ? 0.0d : offer.getDefaultDrainRate(),
                    offer == null ? 0.0d : offer.getDefaultRefillRate());
        }
        if (trader.factionId() > 0 && !economyStore.hasFactionState(trader.economyScope(), offer.getId()))
            return new ShopEconomyStore.EconomyState(0L, 0L, offer.getDefaultStockLimit(), 0.0d, 0.0d);
        return trader.factionId() > 0 ? economyStore.stateForWithoutTick(trader.economyScope(), offer)
                : economyStore.stateFor(trader.economyScope(), offer);
    }

    public ShopEconomyStore.EconomyTickStatus economyTickStatusFor(Player player, ShopOffer offer) {
        if (offer == null || economyStore == null) {
            return ShopEconomyStore.EconomyTickStatus.inactive();
        }
        String scope = ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null));
        return economyStore.tickStatusFor(scope, offer);
    }

    public ShopEconomyStore.EconomyTickStatus economyTickStatusFor(Trader trader, ShopOffer offer) {
        if (offer == null || economyStore == null) return ShopEconomyStore.EconomyTickStatus.inactive();
        return economyStore.tickStatusFor(trader == null ? "global" : trader.economyScope(), offer);
    }

    public long nextEconomyTickAt(Trader trader) {
        return economyStore == null || trader != null && trader.factionId() > 0 ? 0L
                : economyStore.nextScopeTickAt(trader == null ? "global" : trader.economyScope());
    }

    public void forceEconomyTick(Player player, Trader trader) {
        if (player == null || !player.isAdmin() || trader != null && trader.factionId() > 0) return;
        String scope = trader == null ? currentShopZone(player).map(ShopEconomyStore::scopeFor).orElse("global")
                : trader.economyScope();
        economyStore.requestImmediateScopeTick(scope);
    }

    public ShopOffer dynamicEconomyOffer(Player player, ShopOffer offer, int quantity) {
        if (offer == null || !offer.isSystemOffer()) {
            return offer;
        }
        int effectiveQuantity = Math.max(1, quantity);
        long requestedAmount = (long) Math.max(1, offer.getAmount()) * effectiveQuantity;
        int amount = requestedAmount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requestedAmount;
        ShopEconomyStore.EconomyState state = s.dynamicEconomyEnabled && economyStore != null
                ? economyStore.stateFor(ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)), offer)
                : null;
        DynamicEconomyPrices prices = dynamicEconomyPrices(offer, state, amount, s.dynamicEconomyEnabled);
        long buyPrice = prices.buyPrice();
        long sellPrice = prices.sellPrice();
        if (sellPrice <= buyPrice && offer.getBasePrice() > 0.0d && buyPrice < Long.MAX_VALUE) {
            sellPrice = buyPrice + 1L;
        }
        return offer.economyCopy(amount, prices.averageUnitPrice(), buyPrice, sellPrice);
    }

    public ShopOffer configuredSystemOffer(Player player, ShopOffer offer) {
        return offer;
    }

    public ShopPurchaseResult resetSystemOfferStockToTarget(Player player, ShopOffer offer) {
        if (player == null || !player.isAdmin()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.stock.update.failed", player));
        }
        if (offer == null || !offer.isSystemOffer() || economyStore == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.OFFER_NOT_FOUND, t.get("tc.shop.stock.offer.not.found", player));
        }
        String scope = ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null));
        ShopEconomyStore.EconomyState state = economyStore.stateFor(scope, offer);
        long targetStock = state.targetStock() > 0L ? state.targetStock() : offer.getDefaultTargetStock();
        boolean updated = economyStore.configure(scope, offer.getId(), Math.max(0L, targetStock),
                state.drainRate(), state.refillRate());
        if (!updated) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.stock.update.failed", player));
        }
        return ShopPurchaseResult.success(t.get("tc.shop.ui.admin.stock.reset.done", player)
                .replace("PH_OFFER", offer.getId())
                .replace("PH_STOCK", String.valueOf(Math.max(0L, targetStock))), offer);
    }

    public String systemBuyDisabledReason(Player player, ShopOffer offer, int quantity) {
        if (offer == null || !offer.isSystemOffer() || !offer.canPlayerBuyFromSystem()) {
            return "";
        }
        if (!isSystemShopAvailableFor(player)) {
            return t.get("tc.shop.system.disabled", player);
        }
        if (economyStore == null) {
            return "";
        }
        ShopOffer effectiveOffer = dynamicEconomyOffer(player, offer, Math.max(1, quantity));
        boolean available = economyStore.canSellToPlayer(
                ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)), effectiveOffer);
        return available ? "" : t.get("tc.shop.dynamic.stock.empty", player);
    }

    public String systemSellDisabledReason(Player player, ShopOffer offer, int quantity) {
        if (offer == null || !offer.isSystemOffer() || !offer.canPlayerSellToSystem()) {
            return "";
        }
        if (!isSystemShopAvailableFor(player)) {
            return t.get("tc.shop.system.disabled", player);
        }
        if (economyStore == null) {
            return "";
        }
        ShopOffer effectiveOffer = dynamicEconomyOffer(player, offer, Math.max(1, quantity));
        ShopEconomyStore.EconomyCheck check = economyStore.canBuyFromPlayer(
                ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null)), player.getDbID(), effectiveOffer);
        if (check.allowed()) {
            return "";
        }
        String key = check.messageKey().isBlank() ? "tc.shop.stock.update.failed" : check.messageKey();
        return t.get(key, player);
    }

    static DynamicEconomyPrices dynamicEconomyPrices(ShopOffer offer, ShopEconomyStore.EconomyState state, int amount) {
        return dynamicEconomyPrices(offer, state, amount, true);
    }

    static DynamicEconomyPrices dynamicEconomyPrices(ShopOffer offer, ShopEconomyStore.EconomyState state, int amount,
            boolean stockDependent) {
        DynamicEconomyPricing.Prices prices = DynamicEconomyPricing.prices(offer, state, amount, stockDependent);
        return new DynamicEconomyPrices(prices.buyPrice(), prices.sellPrice(), prices.averageUnitPrice());
    }

    record DynamicEconomyPrices(long buyPrice, long sellPrice, double averageUnitPrice) {
    }

    public int reloadSystemOffers() {
        if (service == null || s == null) {
            return 0;
        }
        List<ShopOffer> offers = SystemOfferFile.load((Shop) this, s.systemOffersFile, s.generateDefinitionExports,
                s.systemShopCurrency);
        service.replaceSystemOffers(offers);
        refreshCompleteCatalogBaseUnitPrices();
        reconcileEconomyState();
        return offers.size();
    }

    private void refreshCompleteCatalogBaseUnitPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (ShopOffer offer : SystemOfferFile.loadCompleteCatalog((Shop) this)) {
            if (offer.getItemName() == null || offer.getItemName().isBlank() || offer.getAmount() <= 0) continue;
            prices.putIfAbsent(completeCatalogKey(offer.getItemName(), offer.getItemVariant()),
                    Math.max(0.0d, offer.getBasePrice()) / offer.getAmount());
        }
        completeCatalogBaseUnitPrices = Map.copyOf(prices);
    }

    private static String completeCatalogKey(String itemName, int itemVariant) {
        return itemName.trim().toLowerCase(Locale.ROOT) + ':' + Math.max(0, itemVariant);
    }

    public void reloadShopZones() {
        if (zoneService != null) {
            zoneService.load();
            reconcileEconomyState();
        }
    }

    public boolean isShopAvailableFor(Player player) {
        if (player == null) {
            return false;
        }
        return isSystemShopAvailableFor(player) || isPluginShopAvailableFor(player);
    }

    public boolean isSystemShopAvailableFor(Player player) {
        if (player == null) {
            return false;
        }
        return zoneService == null
                ? s.systemShopEnabled
                : zoneService.systemShopEnabledFor(player, s.systemShopEnabled);
    }

    public boolean isPluginShopAvailableFor(Player player) {
        if (player == null) return false;
        return zoneService == null ? s.pluginShopEnabled
                : zoneService.pluginShopEnabledFor(player, s.pluginShopEnabled);
    }

    public String shopUnavailableMessage(Player player) {
        return t.get("tc.shop.disabled", player);
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

    public ShopZone setZonePluginShop(long areaId, int pluginShop) {
        return zoneService == null ? null : zoneService.setPluginShopMode(areaId, pluginShop).orElse(null);
    }

    public ShopZone setZoneName(long areaId, String name) {
        return zoneService == null ? null : zoneService.setZoneName(areaId, name).orElse(null);
    }

    public ShopZone syncCurrentZoneName(Player player) {
        return zoneService == null ? null : zoneService.syncCurrentZoneName(player).orElse(null);
    }

    public Optional<ShopZone> createOrEnableCurrentZone(Player player) {
        if (zoneService == null) {
            return Optional.empty();
        }
        Optional<ShopZone> zone = zoneService.createOrEnableCurrentZone(player);
        if (zone.isPresent()) {
            reloadShopZones();
        }
        return zone;
    }

    public boolean isInValidArea(Player player) {
        Area area = player == null ? null : player.getCurrentArea();
        return area != null && area.getID() > 0L;
    }

    public ShopZone setZoneSystemOffersFile(long areaId, String systemOffersFile) {
        return zoneService == null ? null : zoneService.setSystemOffersFile(areaId, systemOffersFile).orElse(null);
    }

    public ShopPurchaseResult resetCurrentZoneStocksToTarget(Player player) {
        Optional<ShopZone> current = currentShopZone(player);
        if (player == null || !player.isAdmin() || current.isEmpty()) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.zone.no.area", player));
        }
        if (economyStore == null) {
            return ShopPurchaseResult.failure(ShopErrorCode.INVALID_ARGUMENT, t.get("tc.shop.stock.update.failed", player));
        }
        List<ShopOffer> offers = listSystemOffers(player).stream()
                .filter(ShopOffer::isSystemOffer)
                .toList();
        int updated = economyStore.resetStocksToTarget(ShopEconomyStore.scopeFor(current.get()), offers);
        reconcileEconomyState();
        return ShopPurchaseResult.success(t.get("tc.shop.ui.zone.stocks.reset.done", player)
                .replace("PH_COUNT", String.valueOf(updated)), null);
    }

    public void setCurrentZoneOfferFile(Player player, String configuredFile) {
        Optional<ShopZone> current = currentShopZone(player);
        if (current.isEmpty()) {
            player.sendTextMessage(c.warning + t.get("tc.shop.zone.no.area", player));
            return;
        }
        String value = configuredFile == null ? "" : configuredFile.trim();
        if (value.equalsIgnoreCase("default") || value.equals("-")) {
            value = "";
        }
        ShopZone updated = setZoneSystemOffersFile(current.get().getAreaId(), value);
        if (updated == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.ui.zone.offers.update.failed", player));
            return;
        }
        reconcileEconomyState();
        player.sendTextMessage(c.okay + t.get("tc.shop.ui.zone.offers.updated", player)
                .replace("PH_AREA", updated.getAreaName())
                .replace("PH_FILE", updated.getSystemOffersFile().isBlank()
                        ? t.get("tc.shop.ui.zone.offers.default", player)
                        : updated.getSystemOffersFile()));
    }

    public void configureStock(Player player, String offerId, String arguments) {
        if (economyStore == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.stock.update.failed", player));
            return;
        }
        String[] parts = arguments == null ? new String[0] : arguments.trim().split("\\s+");
        if (offerId == null || offerId.isBlank() || parts.length < 1) {
            player.sendTextMessage(c.warning + t.get("tc.shop.stock.usage", player)
                    .replace("PH_PLUGIN_CMD", s.shopCommand));
            return;
        }
        ShopOffer offer = findSystemOfferFor(player, offerId).orElse(null);
        if (offer == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.stock.offer.not.found", player));
            return;
        }
        long stock = parseNonNegativeLong(parts[0], -1L);
        if (stock < 0L) {
            player.sendTextMessage(c.warning + t.get("tc.shop.stock.usage", player)
                    .replace("PH_PLUGIN_CMD", s.shopCommand));
            return;
        }
        String scope = ShopEconomyStore.scopeFor(currentShopZone(player).orElse(null));
        boolean updated = economyStore.configure(scope, offer.getId(), stock, 0.0d, 0.0d);
        player.sendTextMessage((updated ? c.okay : c.error) + (updated
                ? t.get("tc.shop.stock.updated", player)
                        .replace("PH_OFFER", offer.getId())
                        .replace("PH_STOCK", String.valueOf(stock))
                : t.get("tc.shop.stock.update.failed", player)));
    }

    public void configureEconomy(Player player, String offerId, String arguments) {
        if (economyStore == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.economy.update.failed", player));
            return;
        }
        if (offerId == null || offerId.isBlank() || arguments == null || arguments.trim().isBlank()) {
            player.sendTextMessage(c.warning + t.get("tc.shop.economy.usage", player)
                    .replace("PH_PLUGIN_CMD", s.shopCommand));
            return;
        }
        ShopOffer offer = findSystemOfferFor(player, offerId).orElse(null);
        if (offer == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.stock.offer.not.found", player));
            return;
        }
        player.sendTextMessage(c.warning + t.get("tc.shop.economy.json.only", player)
                .replace("PH_OFFER", offer.getId())
                .replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    public boolean showShopZoneIndicator() {
        return s != null && s.showShopZoneIndicator;
    }

    public void openShopUI(Player player) {
        openShopOverlay(player, null);
    }

    /** Opens the trader-specific overlay; this does not depend on a shop zone. */
    public void openTraderUI(Player player, Trader trader) {
        openShopOverlay(player, trader);
    }

    private void openShopOverlay(Player player, Trader trader) {
        ShopOverlay existing = (ShopOverlay) player.getAttribute("oz.shop.ui.overlay");
        if (existing != null) player.deleteAttribute("oz.shop.ui.overlay");
        ShopOverlay overlay = trader == null ? new ShopOverlay((Shop) this, player)
                : new ShopOverlay((Shop) this, player, trader);
        player.addUIElement(overlay, UITarget.Modal);
        player.setAttribute("oz.shop.ui.overlay", overlay);
    }

    public void sendOfferList(Player player) {
        List<ShopOffer> offers = listOffers().stream()
                .filter(ShopOffer::isEnabled)
                .filter(offer -> offer.isSystemOffer() ? isSystemShopAvailableFor(player) : isPluginShopAvailableFor(player))
                .toList();
        if (offers.isEmpty()) {
            player.sendTextMessage(c.info + t.get("tc.shop.no.offers", player));
            return;
        }
        player.sendTextMessage(c.okay + t.get("tc.shop.title", player));
        for (ShopOffer offer : offers) {
            String currency = offer.getCurrencyIdentifier().isBlank()
                    ? t.get("tc.shop.default.currency", player)
                    : offer.getCurrencyIdentifier();
            String label = offer.getItemName().isBlank() ? offer.getTitle(player)
                    : offer.getAmount() + "x "
                            + ShopItemNames.label(offer.getItemName(), offer.getItemVariant(), offer.getTitle(player),
                                    player.getLanguage());
            player.sendTextMessage(c.info + offer.getId() + c.text + " - " + label + " ("
                    + offer.getPrice(player) + " " + currency + ")");
        }
        player.sendTextMessage(c.text + t.get("tc.shop.usage", player).replace("PH_PLUGIN_CMD", s.shopCommand));
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseNonNegativeLong(String value, long fallback) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.trim());
            return parsed >= 0L ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseNonNegativeDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value == null ? "" : value.trim());
            return parsed >= 0.0d ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static ShopEconomyStore.EconomyUpdate parseEconomyUpdate(String arguments) {
        Long targetStock = null;
        Long stockLimit = null;
        String stockMode = null;
        Double minPriceMultiplier = null;
        Double maxPriceMultiplier = null;
        Double spreadPercent = null;
        Double drainPercent = null;
        Long drainMax = null;
        Long restockMax = null;
        Long perPlayerDailySellLimit = null;
        Long globalDailySellLimit = null;
        boolean parsedAny = false;
        String[] parts = arguments == null ? new String[0] : arguments.trim().split("\\s+");
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                return null;
            }
            String key = part.substring(0, separator).trim().toLowerCase();
            String value = part.substring(separator + 1).trim();
            switch (key) {
                case "target", "targetstock" -> {
                    targetStock = parseNullableNonNegativeLong(value);
                    if (targetStock == null) return null;
                    parsedAny = true;
                }
                case "limit", "stocklimit", "maxstock" -> {
                    stockLimit = parseNullableNonNegativeLong(value);
                    if (stockLimit == null) return null;
                    parsedAny = true;
                }
                case "mode", "stockmode" -> {
                    ShopStockMode mode = parseStockMode(value);
                    if (mode == null) return null;
                    stockMode = mode.name();
                    parsedAny = true;
                }
                case "min", "minmultiplier", "minpricemultiplier" -> {
                    minPriceMultiplier = parseNullableNonNegativeDouble(value);
                    if (minPriceMultiplier == null) return null;
                    parsedAny = true;
                }
                case "max", "maxmultiplier", "maxpricemultiplier" -> {
                    maxPriceMultiplier = parseNullableNonNegativeDouble(value);
                    if (maxPriceMultiplier == null) return null;
                    parsedAny = true;
                }
                case "spread", "spreadpercent" -> {
                    spreadPercent = parseNullableNonNegativeDouble(value);
                    if (spreadPercent == null) return null;
                    parsedAny = true;
                }
                case "drainpercent" -> {
                    drainPercent = parseNullableNonNegativeDouble(value);
                    if (drainPercent == null) return null;
                    parsedAny = true;
                }
                case "drainmax" -> {
                    drainMax = parseNullableNonNegativeLong(value);
                    if (drainMax == null) return null;
                    parsedAny = true;
                }
                case "restockmax", "refillmax" -> {
                    restockMax = parseNullableNonNegativeLong(value);
                    if (restockMax == null) return null;
                    parsedAny = true;
                }
                case "playerlimit", "perplayerlimit", "perplayerdailyselllimit" -> {
                    perPlayerDailySellLimit = parseNullableNonNegativeLong(value);
                    if (perPlayerDailySellLimit == null) return null;
                    parsedAny = true;
                }
                case "globallimit", "globaldailyselllimit" -> {
                    globalDailySellLimit = parseNullableNonNegativeLong(value);
                    if (globalDailySellLimit == null) return null;
                    parsedAny = true;
                }
                default -> {
                    return null;
                }
            }
        }
        if (!parsedAny) {
            return null;
        }
        return new ShopEconomyStore.EconomyUpdate(targetStock, stockLimit, stockMode, minPriceMultiplier,
                maxPriceMultiplier, spreadPercent, drainPercent, drainMax, restockMax,
                perPlayerDailySellLimit, globalDailySellLimit);
    }

    private static Long parseNullableNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.trim());
            return parsed >= 0L ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseNullableNonNegativeDouble(String value) {
        try {
            double parsed = Double.parseDouble(value == null ? "" : value.trim());
            return parsed >= 0.0d ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ShopStockMode parseStockMode(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        try {
            return ShopStockMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static ShopPurchaseResult unavailable(String message) {
        return ShopPurchaseResult.failure(ShopErrorCode.CALLBACK_FAILED, message);
    }

    private void reconcileEconomyState() { reconcileEconomyState(false); }

    private void reconcileEconomyState(boolean force) {
        if (economyStore != null && service != null) {
            economyStore.setTickIntervalHours(s.economyTickIntervalHours);
            for (ShopEconomyStore.ScopeTickResult result : (force
                    ? economyStore.reconcileNow(service.listSystemOffers(), listShopZones())
                    : economyStore.reconcile(service.listSystemOffers(), listShopZones()))) {
                EconomyReportService.report((Shop) this, t, s, result, service.listSystemOffers(), null, s.dynamicEconomyEnabled);
                refreshOpenShopOverlays(result);
            }
            WalletBridge wallet = new WalletBridge((Shop) this);
            String currency = s.systemShopCurrency.isBlank() ? wallet.defaultCurrencyIdentifier() : s.systemShopCurrency;
            if (traderService != null && wallet.hasSystemAccountApi() && !wallet.worldSystemAccountId().isBlank()) {
                for (Trader trader : listTraders()) {
                    ShopEconomyStore.ScopeTickResult result = traderService.reconcileEconomy(trader,
                            listTraderSystemOffers(trader), economyStore, wallet, currency, s.dynamicEconomyEnabled, force);
                    EconomyReportService.report((Shop) this, t, s, result, listTraderSystemOffers(trader), trader,
                            s.dynamicEconomyEnabled);
                    refreshOpenShopOverlays(result);
                }
            }
        }
    }

    private void refreshOpenShopOverlays(ShopEconomyStore.ScopeTickResult result) {
        if (result == null || !result.changed()) return;
        for (Player player : Server.getAllPlayers()) {
            Object overlay = player.getAttribute("oz.shop.ui.overlay");
            if (overlay instanceof ShopOverlay shopOverlay) shopOverlay.refreshAfterEconomyTick(result.scope());
        }
    }

    private void initializeEconomyScopes() {
        if (economyStore == null || service == null) return;
        economyStore.setTickIntervalHours(s.economyTickIntervalHours);
        economyStore.initializeScope("global");
        for (ShopZone zone : listShopZones()) economyStore.initializeScope(ShopEconomyStore.scopeFor(zone));
        for (Trader trader : listTraders()) economyStore.initializeScope(trader.economyScope());
    }

    private void makeTrader(Player player) {
        player.getNpcInLineOfSight(10f, npc -> {
            if (npc == null) {
                player.sendTextMessage(c.warning + t.get("tc.shop.trader.no.npc", player));
                return;
            }
            registerTrader(player, npc);
        });
    }

    public void createTrader(Player player) {
        createTrader(player, new Random().nextBoolean());
    }

    public void createTrader(Player player, boolean male) {
        if (player == null || !player.isAdmin()) return;
        createTrader(player, 0, male);
    }

    public boolean canCreateFactionTrader(Player player) {
        if (player == null || traderService == null) return false;
        FactionTraderBridge factions = new FactionTraderBridge(this);
        Integer factionId = factions.factionIdFor(player.getDbID());
        if (factionId == null || factionId <= 0 || !factions.canManage(player.getDbID())) return false;
        Integer capacity = factions.capacity(factionId);
        int count = traderService.factionTraderCount(factionId);
        return capacity != null && count >= 0 && count < capacity
                && factions.accountId(factionId) != null && factions.factionName(factionId) != null;
    }

    public void createFactionTrader(Player player) {
        createFactionTrader(player, new Random().nextBoolean());
    }

    public void createFactionTrader(Player player, boolean male) {
        if (player == null) return;
        if (!canCreateFactionTrader(player)) {
            player.sendTextMessage(c.error + t.get("tc.shop.trader.faction.create.denied", player));
            return;
        }
        FactionTraderBridge factions = new FactionTraderBridge(this);
        Integer factionId = factions.factionIdFor(player.getDbID());
        if (factionId != null) createTrader(player, factionId, male);
    }

    private void createTrader(Player player, int factionId, boolean male) {
        if (player == null) return;
        TraderGeneratorConfig config = traderGeneratorConfig == null
                ? new TraderGeneratorConfig(List.of(), List.of(), List.of(), List.of()) : traderGeneratorConfig;
        Random random = new Random();
        net.risingworld.api.definitions.Npcs.NpcDefinition dummy = Definitions.getNpcDefinition("dummy");
        if (dummy == null) {
            logger().error("Could not create trader: NPC definition 'dummy' was not found");
            player.sendTextMessage(c.error + t.get("tc.shop.trader.create.failed", player));
            return;
        }
        Npc npc = World.spawnNpc(dummy.id, male ? 0 : 1, player.getPosition(), player.getRotation());
        if (npc == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.trader.create.failed", player));
            return;
        }
        String name = t.get(male ? "tc.shop.trader.prefix.male" : "tc.shop.trader.prefix.female", player)
                + " " + config.randomName(male, random);
        List<String> outfit = config.randomOutfit(random);
        GeneratedTraderAppearance appearance = new GeneratedTraderAppearance(name, male, List.copyOf(outfit),
                randomSkinColor(random), random(HAIR_COLORS, random), random(EYE_COLORS, random),
                randomHairstyle(male, random),
                (byte) (male && random.nextInt(100) >= 20 ? random.nextInt(14) : -1),
                (byte) random.nextInt(5));
        applyGeneratedTraderAppearance(npc, appearance);
        boolean registered = factionId > 0 ? registerFactionTrader(player, npc, factionId, appearance.name())
                : registerTrader(player, npc);
        if (!registered) {
            if (!npc.isDead()) npc.delete();
            return;
        }
        executeDelayed(0.1f, () -> initializeGeneratedTrader(npc, appearance));
        executeDelayed(0.5f, () -> initializeGeneratedTrader(npc, appearance));
        executeDelayed(1.0f, () -> initializeGeneratedTrader(npc, appearance));
    }

    private void initializeGeneratedTrader(Npc npc, GeneratedTraderAppearance appearance) {
        if (npc == null || npc.isDead()) return;
        verifyGeneratedTraderAppearance(npc, appearance);
        byte[] saved = traderService == null ? null : traderService.clothing(npc.getGlobalID());
        if (saved != null) npc.getClothes().deserialize(saved);
        else applyTraderOutfit(npc, appearance.clothing());
    }

    public boolean randomTraderClothing(Player player, Trader trader) {
        if (!canManageTrader(player, trader)) return false;
        Npc npc = World.getNpc(trader.npcId());
        if (npc == null || npc.isDead() || npc.getClothes() == null || traderGeneratorConfig == null) return false;
        List<String> outfit = traderGeneratorConfig.randomOutfit(new Random());
        if (outfit.isEmpty()) return false;
        byte[] previous = npc.getClothes().serialize();
        applyTraderOutfit(npc, outfit);
        if (traderService.saveClothing(trader.npcId(), npc.getClothes().serialize())) return true;
        npc.getClothes().deserialize(previous);
        return false;
    }

    public boolean copyTraderClothing(Player player, Trader trader) {
        if (!canManageTrader(player, trader)) return false;
        Npc npc = World.getNpc(trader.npcId());
        if (npc == null || npc.isDead() || npc.getClothes() == null || player.getClothes() == null) return false;
        byte[] clothing = player.getClothes().serialize();
        if (!traderService.saveClothing(trader.npcId(), clothing)) return false;
        npc.getClothes().deserialize(clothing);
        return true;
    }

    private void applyGeneratedTraderAppearance(Npc npc, GeneratedTraderAppearance appearance) {
        npc.setName(appearance.name());
        npc.setLocked(true);
        if (traderService != null) traderService.applyNpcFlags(npc);
        Skin skin = npc.getSkin();
        if (skin != null) {
            skin.setGender(appearance.male() ? Skin.Gender.Male : Skin.Gender.Female);
            skin.setSkinColor(appearance.skinColor());
            skin.setHairColor(appearance.hairColor());
            skin.setEyeColor(appearance.eyeColor());
            skin.setHairstyle(appearance.hairstyle());
            skin.setBeard(appearance.beard());
            skin.setVariation(appearance.variation());
        }
    }

    private void verifyGeneratedTraderAppearance(Npc npc, GeneratedTraderAppearance appearance) {
        if (npc == null || npc.isDead() || generatedTraderAppearanceMatches(npc, appearance)) return;
        logger().warn("Repairing generated trader appearance for NPC " + npc.getGlobalID()
                + ": expected name '" + appearance.name() + "', actual name '" + npc.getName() + "'");
        applyGeneratedTraderAppearance(npc, appearance);
        if (!generatedTraderAppearanceMatches(npc, appearance)) {
            logger().warn("Generated trader appearance is still not fully applied for NPC " + npc.getGlobalID());
        }
    }

    private static boolean generatedTraderAppearanceMatches(Npc npc, GeneratedTraderAppearance appearance) {
        if (!appearance.name().equals(npc.getName()) || !npc.isLocked() || !npc.isInvincible()) return false;
        Skin skin = npc.getSkin();
        return npc.getBehaviour() == Npcs.Behaviour.Dummy && !npc.isStatic() && skin != null
                && skin.getGender() == (appearance.male() ? Skin.Gender.Male : Skin.Gender.Female)
                && skin.getSkinColor() == appearance.skinColor()
                && skin.getHairColor() == appearance.hairColor()
                && skin.getEyeColor() == appearance.eyeColor()
                && skin.getHairstyle() == appearance.hairstyle()
                && skin.getBeard() == appearance.beard()
                && skin.getVariation() == appearance.variation();
    }

    private static int randomSkinColor(Random random) {
        int weight = random.nextInt(100);
        return random(weight < 76 ? LIGHT_SKIN_COLORS : weight < 96 ? MEDIUM_SKIN_COLORS : DARK_SKIN_COLORS, random);
    }

    static byte randomHairstyle(boolean male, Random random) {
        int first = male ? MALE_HAIRSTYLE_FIRST : FEMALE_HAIRSTYLE_FIRST;
        int last = male ? MALE_HAIRSTYLE_LAST : FEMALE_HAIRSTYLE_LAST;
        return (byte) (first + random.nextInt(last - first + 1));
    }

    private static int random(int[] values, Random random) {
        return values[random.nextInt(values.length)];
    }

    private static byte random(byte[] values, Random random) {
        return values[random.nextInt(values.length)];
    }

    private void applyTraderOutfit(Npc npc, List<String> clothing) {
        net.risingworld.api.objects.Clothes clothes = npc.getClothes();
        if (clothes == null) {
            logger().warn("Generated trader NPC " + npc.getGlobalID() + " does not expose a Clothes object");
            return;
        }
        clothes.removeAll();
        for (String garment : clothing) {
            ClothingDefinition definition = Definitions.getClothingDefinition(garment);
            if (definition == null) {
                logger().warn("Ignoring unknown trader clothing definition: " + garment);
                continue;
            }
            clothes.add((short) definition.id);
        }
    }

    private boolean registerTrader(Player player, Npc npc) {
        Trader trader = traderService == null ? null : traderService.register(npc, player).orElse(null);
        if (trader == null) {
            player.sendTextMessage(c.error + t.get("tc.shop.trader.create.failed", player));
            return false;
        }
        WalletBridge wallet = new WalletBridge((Shop) this);
        String currency = s.systemShopCurrency.isBlank() ? wallet.defaultCurrencyIdentifier() : s.systemShopCurrency;
        boolean accountReady = wallet.createSystemAccount(trader.accountId(), "TRADER", trader.name(), "OZ - Shop").success();
        boolean funded = accountReady && !currency.isBlank() && wallet.transferWorldToSystemIdempotent(
                trader.accountId(), 1000L, "Trader initial capital", currency,
                "OZ - Shop", "trader:" + trader.npcId() + ":seed").success();
        if (!funded) {
            player.sendTextMessage(c.error + t.get("tc.shop.trader.wallet.failed", player));
            return true;
        }
        player.sendTextMessage(c.okay + t.get("tc.shop.trader.created", player)
                .replace("PH_TRADER", trader.name()).replace("PH_ID", String.valueOf(trader.npcId())));
        return true;
    }

    private boolean registerFactionTrader(Player player, Npc npc, int factionId, String name) {
        FactionTraderBridge factions = new FactionTraderBridge(this);
        String factionName = factions.factionName(factionId);
        Integer actorFaction = factions.factionIdFor(player.getDbID());
        Integer capacity = factions.capacity(factionId);
        if (factionName == null || actorFaction == null || actorFaction != factionId
                || !factions.canManage(player.getDbID()) || capacity == null
                || traderService.factionTraderCount(factionId) >= capacity
                || factions.accountId(factionId) == null) {
            npc.delete();
            player.sendTextMessage(c.error + t.get("tc.shop.trader.faction.create.denied", player));
            return false;
        }
        String file = SystemOfferEditor.factionFileName(factionName, name);
        SystemOfferEditor editor = new SystemOfferEditor((Shop) this);
        boolean created;
        try {
            created = !traderService.factionTraderNameExists(factionId, name, npc.getGlobalID())
                    && editor.createEmpty(file);
        } catch (RuntimeException ex) {
            logger().error("Could not prepare faction trader offer file " + file + ": " + ex.getMessage());
            created = false;
        }
        if (!created) {
            npc.delete();
            player.sendTextMessage(c.error + t.get("tc.shop.trader.create.failed", player));
            return false;
        }
        Trader trader;
        try {
            trader = traderService.registerFaction(npc, player, factionId, file, name).orElse(null);
        } catch (RuntimeException ex) {
            logger().error("Could not register faction trader " + npc.getGlobalID() + ": " + ex.getMessage());
            trader = null;
        }
        if (trader == null) {
            if (!editor.deleteEmpty(file)) logger().error("Could not remove unclaimed faction trader offer file: " + file);
            npc.delete();
            player.sendTextMessage(c.error + t.get("tc.shop.trader.create.failed", player));
            return false;
        }
        player.sendTextMessage(c.okay + t.get("tc.shop.trader.created", player)
                .replace("PH_TRADER", trader.name()).replace("PH_ID", String.valueOf(trader.npcId())));
        return true;
    }

    private Optional<ShopOffer> findTraderOffer(Trader trader, String offerId) {
        if (trader == null || offerId == null || offerId.isBlank()) return Optional.empty();
        return listTraderSystemOffers(trader).stream().filter(offer -> offer.getId().equalsIgnoreCase(offerId.trim()))
                .findFirst();
    }

    public ShopOffer dynamicTraderOffer(Trader trader, ShopOffer offer, int quantity) {
        if (trader == null || offer == null || !offer.isSystemOffer()) return offer;
        int amount = Math.max(1, quantity) * Math.max(1, offer.getAmount());
        ShopEconomyStore.EconomyState state = s.dynamicEconomyEnabled && economyStore != null
                ? trader.factionId() > 0
                        ? economyStore.hasFactionState(trader.economyScope(), offer.getId())
                                ? economyStore.stateForWithoutTick(trader.economyScope(), offer) : null
                        : economyStore.stateFor(trader.economyScope(), offer) : null;
        DynamicEconomyPrices prices = dynamicEconomyPrices(offer, state, amount, s.dynamicEconomyEnabled);
        long sell = prices.sellPrice() <= prices.buyPrice() && offer.getBasePrice() > 0.0d
                ? prices.buyPrice() + 1L : prices.sellPrice();
        return offer.economyCopy(amount, prices.averageUnitPrice(), prices.buyPrice(), sell);
    }

    private void copyDefaultTraderOffers() {
        try {
            Path pluginPath = Paths.get(getPath() == null ? "." : getPath());
            Path destination = pluginPath.resolve("default-trader.json");
            Path packaged = pluginPath.resolve("default-trader.default.json");
            if (Files.notExists(destination) && Files.exists(packaged)) Files.copy(packaged, destination);
        } catch (java.io.IOException ex) {
            logger().error("Could not prepare default trader offers: " + ex.getMessage());
        }
    }

    private TraderGeneratorConfig loadTraderGeneratorConfig() {
        try {
            Path pluginPath = Paths.get(getPath() == null ? "." : getPath());
            Path destination = pluginPath.resolve("traders-config.json");
            Path packaged = pluginPath.resolve("traders-config.default.json");
            if (Files.notExists(destination) && Files.exists(packaged)) Files.copy(packaged, destination);
            return TraderGeneratorConfig.load(destination);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            logger().error("Could not prepare trader generator config: " + ex.getMessage());
            return new TraderGeneratorConfig(List.of(), List.of(), List.of(), List.of());
        }
    }

    private void syncEconomyTimer() {
        if (s != null && economyStore != null) {
            startEconomyTimer();
        } else {
            stopEconomyTimer();
        }
    }

    private void startEconomyTimer() {
        if (economyTimer != null && economyTimer.isActive() && !economyTimer.isKilled()) {
            return;
        }
        stopEconomyTimer();
        float intervalSeconds = 5f;
        economyTimer = new Timer(intervalSeconds, intervalSeconds, -1, () -> {
            if (s == null || economyStore == null) {
                return;
            }
            try {
                reconcileEconomyState();
            } catch (RuntimeException ex) {
                logger().error("Shop economy timer failed: " + ex.getMessage());
            }
        });
        economyTimer.start();
    }

    private void stopEconomyTimer() {
        if (economyTimer != null && !economyTimer.isKilled()) {
            economyTimer.kill();
        }
        economyTimer = null;
    }

    private Optional<ShopOffer> findSystemOfferFor(Player player, String offerId) {
        if (player == null || offerId == null || offerId.isBlank()) {
            return Optional.empty();
        }
        String offerFile = systemOffersFileFor(player);
        if (offerFile.equals(s.systemOffersFile)) {
            return service.findOffer(offerId).filter(ShopOffer::isSystemOffer);
        }
        return SystemOfferFile.load((Shop) this, offerFile, s.generateDefinitionExports, s.systemShopCurrency).stream()
                .filter(offer -> offer.getId().equalsIgnoreCase(offerId.trim()))
                .findFirst();
    }

    private String systemOffersFileFor(Player player) {
        return currentShopZone(player)
                .map(ShopZone::getSystemOffersFile)
                .filter(file -> !file.isBlank())
                .orElse(s.systemOffersFile);
    }
}
