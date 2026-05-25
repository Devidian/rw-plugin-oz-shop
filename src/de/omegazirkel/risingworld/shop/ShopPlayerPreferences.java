package de.omegazirkel.risingworld.shop;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.objects.Player;

public final class ShopPlayerPreferences {
    public static final String LAYOUT_KEY = "oz.shop.layout";
    public static final String LAYOUT_CARD = "CARD";
    public static final String LAYOUT_LIST = "LIST";

    private ShopPlayerPreferences() {
    }

    public static void load(Player player) {
        int dbId = player.getDbID();
        if (!player.hasAttribute(LAYOUT_KEY)) {
            player.setAttribute(LAYOUT_KEY, Shop.ps.getString(dbId, LAYOUT_KEY).orElse(LAYOUT_CARD));
        }
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
}
