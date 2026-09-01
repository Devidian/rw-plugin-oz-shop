package de.omegazirkel.risingworld.shop;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.JsonSettingsFile;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;

public class PluginSettings {
    private static PluginSettings instance;
    private static Shop plugin;

    public String shopCommand = "shop";
    public boolean enableWelcomeMessage = false;
    public String systemOffersFile = "system-offers.json";
    public String systemShopCurrency = "";
    public boolean systemShopEnabled = true;
    public boolean shopEnabled = true;
    public boolean requireShopZone = false;
    public String shopZonesFile = "shop-zones.json";
    public boolean showShopZoneIndicator = true;
    public boolean generateDefinitionExports = false;
    public boolean dynamicEconomyEnabled = false;
    public int economyTickIntervalHours = 1;
    public boolean economyReportGlobal = false;
    public boolean economyReportZones = false;
    public boolean economyReportTraders = false;
    public String economyReportRecipients = "";
    public boolean exposeShopZones = true;
    private Path settingsFile;

    private static OZLogger logger() {
        return Shop.logger();
    }

    public static PluginSettings getInstance(Shop p) {
        plugin = p;
        return getInstance();
    }

    public static PluginSettings getInstance() {
        if (instance == null) {
            instance = new PluginSettings();
        }
        return instance;
    }

    private PluginSettings() {
    }

    public void initSettings() {
        initSettings(JsonSettingsFile.worldSettingsFile(plugin.getPath() != null ? plugin.getPath() : ".").toString());
    }

    public void initSettings(String filePath) {
        settingsFile = Paths.get(filePath);
        Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.json");
        Path legacySettingsFile = settingsFile.resolveSibling("settings.properties");
        try {
            if (JsonSettingsFile.migrateLegacyProperties(legacySettingsFile, settingsFile))
                logger().info("Migrated legacy settings.properties to " + settingsFile.getFileName());
            if (Files.notExists(settingsFile) && Files.exists(defaultSettingsFile))
                JsonSettingsFile.writeFlatAtomically(settingsFile, JsonSettingsFile.loadFlat(defaultSettingsFile));
            Properties settings = loadSettings(settingsFile);
            Properties defaults = loadSettings(defaultSettingsFile);

            shopCommand = settings.getProperty("shopCommand", defaults.getProperty("shopCommand", "shop"));
            enableWelcomeMessage = settings
                    .getProperty("sendPluginWelcome", defaults.getProperty("sendPluginWelcome", "false"))
                    .contentEquals("true");
            systemOffersFile = settings.getProperty("systemOffersFile",
                    defaults.getProperty("systemOffersFile", "system-offers.json"));
            systemShopCurrency = settings.getProperty("systemShopCurrency",
                    defaults.getProperty("systemShopCurrency", "")).trim().toUpperCase();
            systemShopEnabled = settings.getProperty("systemShopEnabled",
                    defaults.getProperty("systemShopEnabled", "true")).contentEquals("true");
            shopEnabled = settings.getProperty("shopEnabled", defaults.getProperty("shopEnabled", "true"))
                    .contentEquals("true");
            requireShopZone = settings.getProperty("requireShopZone",
                    defaults.getProperty("requireShopZone", "false")).contentEquals("true");
            shopZonesFile = settings.getProperty("shopZonesFile",
                    defaults.getProperty("shopZonesFile", "shop-zones.json"));
            showShopZoneIndicator = settings.getProperty("showShopZoneIndicator",
                    defaults.getProperty("showShopZoneIndicator", "true")).contentEquals("true");
            generateDefinitionExports = settings.getProperty("generateDefinitionExports",
                    defaults.getProperty("generateDefinitionExports", "false")).contentEquals("true");
            dynamicEconomyEnabled = settings.getProperty("dynamicEconomyEnabled",
                    defaults.getProperty("dynamicEconomyEnabled", "false")).contentEquals("true");
            economyTickIntervalHours = Math.max(1, Integer.parseInt(settings.getProperty("economyTickIntervalHours",
                    defaults.getProperty("economyTickIntervalHours", "1"))));
            economyReportGlobal = settings.getProperty("economyReportGlobal",
                    defaults.getProperty("economyReportGlobal", "false")).contentEquals("true");
            economyReportZones = settings.getProperty("economyReportZones",
                    defaults.getProperty("economyReportZones", "false")).contentEquals("true");
            economyReportTraders = settings.getProperty("economyReportTraders",
                    defaults.getProperty("economyReportTraders", "false")).contentEquals("true");
            economyReportRecipients = settings.getProperty("economyReportRecipients",
                    defaults.getProperty("economyReportRecipients", "")).trim();
            exposeShopZones = settings.getProperty("exposeShopZones",
                    defaults.getProperty("exposeShopZones", "true")).contentEquals("true");

            logger().info((plugin == null ? "OZShop" : plugin.getName()) + " Plugin settings loaded");
            logger().info("Shop command is /" + shopCommand);
            logger().info("System offers file is " + systemOffersFile);
            logger().info("System shop currency is " + (systemShopCurrency.isBlank() ? "Wallet default" : systemShopCurrency));
            logger().info("Game definition exports are " + (generateDefinitionExports ? "enabled" : "disabled"));
            logger().info("Dynamic economy is " + (dynamicEconomyEnabled ? "enabled" : "disabled"));
            logger().info("System shop is " + (systemShopEnabled ? "enabled" : "disabled"));
            logger().info("Shop access is " + (shopEnabled ? "enabled" : "disabled")
                    + (requireShopZone ? " and zone-gated" : " globally available"));
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public List<AdminSettingsEntry> adminSettingsEntries() {
        return List.of(
                AdminSettingsEntry.group("general", "General", "Command and welcome behavior."),
                entry("shopCommand", "Shop command", "Chat command used to list and buy shop offers.", shopCommand,
                        "shop", AdminSettingsType.STRING),
                entry("sendPluginWelcome", "Welcome message", "Shows a short Shop message when a player joins.",
                        enableWelcomeMessage, "false", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("systemShop", "System shop", "System offer file and system-shop behavior."),
                entry("systemOffersFile", "System offers file", "JSON file used for admin-managed system offers.",
                        systemOffersFile, "system-offers.json", AdminSettingsType.STRING),
                entry("systemShopCurrency", "System shop currency",
                        "Central Wallet currency for all system-shop offers; empty uses Wallet default or legacy offer currency.",
                        systemShopCurrency, "", AdminSettingsType.STRING),
                entry("systemShopEnabled", "System shop enabled",
                        "Allows system-shop offers unless a shop area overrides this value.", systemShopEnabled, "true",
                        AdminSettingsType.BOOLEAN),
                entry("generateDefinitionExports", "Generate definition exports",
                        "Writes generated item and recipe export JSON files next to system offers.",
                        generateDefinitionExports, "false", AdminSettingsType.BOOLEAN),
                entry("dynamicEconomyEnabled", "Dynamic economy enabled",
                        "Enables stock-based buy/sell price multipliers; stock rules stay active when disabled.",
                        dynamicEconomyEnabled, "false", AdminSettingsType.BOOLEAN),
                entry("economyTickIntervalHours", "Economy tick interval hours",
                        "Hours between automatic stock reconciliation ticks. Fractions accumulate until at least one item changes.",
                        economyTickIntervalHours, "1", AdminSettingsType.INTEGER),
                entry("economyReportGlobal", "Global economy reports", "Sends a report after changed global stock ticks.",
                        economyReportGlobal, "false", AdminSettingsType.BOOLEAN),
                entry("economyReportZones", "Zone economy reports", "Sends a report after changed zone stock ticks.",
                        economyReportZones, "false", AdminSettingsType.BOOLEAN),
                entry("economyReportTraders", "Trader economy reports", "Reserves reports for changed trader stock ticks.",
                        economyReportTraders, "false", AdminSettingsType.BOOLEAN),
                entry("economyReportRecipients", "Economy report recipients",
                        "Comma-separated exact player names; append ;de or ;en to choose each report language.", economyReportRecipients, "",
                        AdminSettingsType.STRING),
                AdminSettingsEntry.group("shopAccess", "Shop access", "Player shop access and shop-area behavior."),
                entry("shopEnabled", "Shop enabled", "Allows players to use the shop.", shopEnabled, "true",
                        AdminSettingsType.BOOLEAN),
                entry("requireShopZone", "Require shop zone",
                        "Restricts shop access to areas marked as shop areas unless the player is an admin.",
                        requireShopZone, "false", AdminSettingsType.BOOLEAN),
                entry("shopZonesFile", "Shop zones file", "JSON file used for admin-managed shop areas.",
                        shopZonesFile, "shop-zones.json", AdminSettingsType.STRING),
                entry("showShopZoneIndicator", "Show shop-zone indicator",
                        "Shows a compact HUD indicator below the LandClaim area info while players are in a shop area.",
                        showShopZoneIndicator, "true", AdminSettingsType.BOOLEAN),
                AdminSettingsEntry.group("exportRoutes", "Export routes", "Route-ready read exposure for manager bridges."),
                entry("exposeShopZones", "Expose shop zones",
                        "Allows bridge/native route layers to expose SQLite shop-zone metadata.",
                        exposeShopZones, "true", AdminSettingsType.BOOLEAN));
    }

    private AdminSettingsEntry entry(String key, String label, String description, Object value, String defaultValue,
            AdminSettingsType type) {
        return new AdminSettingsEntry(key, label, description, String.valueOf(value), defaultValue, type, false,
                newValue -> SettingsFileEditor.writeValue(settingsFile, key, newValue));
    }

    private Properties loadSettings(Path file) throws IOException {
        if (!file.getFileName().toString().endsWith(".properties")) return JsonSettingsFile.loadProperties(file);
        Properties properties = new Properties();
        if (Files.exists(file)) try (FileInputStream input = new FileInputStream(file.toFile())) {
            properties.load(new InputStreamReader(input, "UTF8"));
        }
        return properties;
    }
}
