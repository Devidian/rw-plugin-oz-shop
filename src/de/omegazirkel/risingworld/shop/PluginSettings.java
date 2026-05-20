package de.omegazirkel.risingworld.shop;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.Level;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;

public class PluginSettings {
    private static PluginSettings instance;
    private static Shop plugin;

    public String logLevel = Level.ALL.name();
    public boolean reloadOnChange = true;
    public String shopCommand = "shop";
    public boolean enableWelcomeMessage = false;
    public String systemOffersFile = "system-offers.json";
    private Path settingsFile;

    private static OZLogger logger() {
        return OZLogger.getInstance("OZ.Shop.Settings");
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
        initSettings((plugin.getPath() != null ? plugin.getPath() : ".") + "/settings.properties");
    }

    public void initSettings(String filePath) {
        settingsFile = Paths.get(filePath);
        Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.properties");
        try {
            if (Files.notExists(settingsFile) && Files.exists(defaultSettingsFile)) {
                logger().info("settings.properties not found, copying from settings.default.properties...");
                Files.copy(defaultSettingsFile, settingsFile);
            }
            Properties settings = new Properties();
            Properties defaults = new Properties();
            if (Files.exists(defaultSettingsFile)) {
                try (FileInputStream in = new FileInputStream(defaultSettingsFile.toFile())) {
                    defaults.load(new InputStreamReader(in, "UTF8"));
                }
            }
            if (Files.exists(settingsFile)) {
                try (FileInputStream in = new FileInputStream(settingsFile.toFile())) {
                    settings.load(new InputStreamReader(in, "UTF8"));
                }
            }

            logLevel = settings.getProperty("logLevel", defaults.getProperty("logLevel", "ALL"));
            reloadOnChange = settings.getProperty("reloadOnChange", defaults.getProperty("reloadOnChange", "true"))
                    .contentEquals("true");
            shopCommand = settings.getProperty("shopCommand", defaults.getProperty("shopCommand", "shop"));
            enableWelcomeMessage = settings
                    .getProperty("sendPluginWelcome", defaults.getProperty("sendPluginWelcome", "false"))
                    .contentEquals("true");
            systemOffersFile = settings.getProperty("systemOffersFile",
                    defaults.getProperty("systemOffersFile", "system-offers.json"));

            logger().info(plugin.getName() + " Plugin settings loaded");
            logger().info("Shop command is /" + shopCommand);
            logger().info("System offers file is " + systemOffersFile);
            logger().setLevel(logLevel);
        } catch (IOException ex) {
            logger().error("IOException on initSettings: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public List<AdminSettingsEntry> adminSettingsEntries() {
        return List.of(
                entry("logLevel", "Log level", "Controls Shop logging verbosity.", logLevel, "ALL",
                        AdminSettingsType.STRING),
                entry("reloadOnChange", "Reload on change",
                        "Reloads settings and system offers when settings.properties changes.", reloadOnChange, "true",
                        AdminSettingsType.BOOLEAN),
                entry("shopCommand", "Shop command", "Chat command used to list and buy shop offers.", shopCommand,
                        "shop", AdminSettingsType.STRING),
                entry("sendPluginWelcome", "Welcome message", "Shows a short Shop message when a player joins.",
                        enableWelcomeMessage, "false", AdminSettingsType.BOOLEAN),
                entry("systemOffersFile", "System offers file", "JSON file used for admin-managed system offers.",
                        systemOffersFile, "system-offers.json", AdminSettingsType.STRING));
    }

    private AdminSettingsEntry entry(String key, String label, String description, Object value, String defaultValue,
            AdminSettingsType type) {
        return new AdminSettingsEntry(key, label, description, String.valueOf(value), defaultValue, type, false,
                newValue -> SettingsFileEditor.writeValue(settingsFile, key, newValue));
    }
}
