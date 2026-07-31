package de.omegazirkel.risingworld.shop;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import net.risingworld.api.objects.Player;

public final class ShopPlayerPreferences {
    public static final String LAYOUT_KEY = "oz.shop.layout";
    public static final String LAYOUT_CARD = "CARD";
    public static final String LAYOUT_LIST = "LIST";
    public static final String PLUGIN_PURCHASE_CONFIRMATION_KEY = "oz.shop.plugin-purchase-confirmation.enabled";
    public static final String PLUGIN_PURCHASE_SUCCESS_MESSAGE_KEY = "oz.shop.plugin-purchase-success-message.enabled";

    private ShopPlayerPreferences() {
    }

    public static void load(Player player) {
        int dbId = player.getDbID();
        if (!player.hasAttribute(LAYOUT_KEY)) {
            player.setAttribute(LAYOUT_KEY, Shop.ps.getString(dbId, LAYOUT_KEY).orElse(LAYOUT_CARD));
        }
        String shortcutKey = shortcutKey();
        if (!player.hasAttribute(shortcutKey)) {
            player.setAttribute(shortcutKey, Shop.ps.getBoolean(dbId, shortcutKey).orElse(true));
        }
        loadBoolean(player, PLUGIN_PURCHASE_CONFIRMATION_KEY, true);
        loadBoolean(player, PLUGIN_PURCHASE_SUCCESS_MESSAGE_KEY, true);
    }

    public static String layout(Player player) {
        if (!player.hasAttribute(LAYOUT_KEY)) {
            load(player);
        }
        Object value = player.getAttribute(LAYOUT_KEY);
        return LAYOUT_LIST.equals(value) ? LAYOUT_LIST : LAYOUT_CARD;
    }

    public static void setLayout(Player player, String value) {
        String normalizedValue = LAYOUT_LIST.equals(value) ? LAYOUT_LIST : LAYOUT_CARD;
        player.setAttribute(LAYOUT_KEY, normalizedValue);
        Shop.ps.setString(player.getDbID(), LAYOUT_KEY, normalizedValue);
    }

    public static boolean shortcutVisible(Player player) {
        if (!player.hasAttribute(shortcutKey())) {
            load(player);
        }
        Object value = player.getAttribute(shortcutKey());
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static void setShortcutVisible(Player player, boolean value) {
        String key = shortcutKey();
        player.setAttribute(key, value);
        Shop.ps.setBoolean(player.getDbID(), key, value);
    }

    public static boolean pluginPurchaseConfirmationEnabled(Player player) {
        return booleanPreference(player, PLUGIN_PURCHASE_CONFIRMATION_KEY, true);
    }

    public static void setPluginPurchaseConfirmationEnabled(Player player, boolean value) {
        setBooleanPreference(player, PLUGIN_PURCHASE_CONFIRMATION_KEY, value);
    }

    public static boolean pluginPurchaseSuccessMessageEnabled(Player player) {
        return booleanPreference(player, PLUGIN_PURCHASE_SUCCESS_MESSAGE_KEY, true);
    }

    public static void setPluginPurchaseSuccessMessageEnabled(Player player, boolean value) {
        setBooleanPreference(player, PLUGIN_PURCHASE_SUCCESS_MESSAGE_KEY, value);
    }

    private static void loadBoolean(Player player, String key, boolean defaultValue) {
        if (!player.hasAttribute(key)) {
            player.setAttribute(key, Shop.ps.getBoolean(player.getDbID(), key).orElse(defaultValue));
        }
    }

    private static boolean booleanPreference(Player player, String key, boolean defaultValue) {
        if (!player.hasAttribute(key)) load(player);
        Object value = player.getAttribute(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static void setBooleanPreference(Player player, String key, boolean value) {
        player.setAttribute(key, value);
        Shop.ps.setBoolean(player.getDbID(), key, value);
    }

    private static String shortcutKey() {
        return PluginShortcutVisibility.playerSettingKey(Shop.name);
    }
}
