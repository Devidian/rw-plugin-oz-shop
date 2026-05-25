package de.omegazirkel.risingworld.shop.ui;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopPlayerPreferences;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;

public class ShopPlayerPluginSettings extends PlayerPluginSettings {
    public ShopPlayerPluginSettings(String pluginVersion) {
        this.pluginLabel = Shop.name;
        this.pluginVersion = pluginVersion;
    }

    private I18n t() {
        return I18n.getInstance(Shop.name);
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player uiPlayer) {
        return new BasePlayerPluginSettingsPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                flexWrapper.addChild(playerSettingLayout(uiPlayer));
            }

            protected OZUIElement playerSettingLayout(Player uiPlayer) {
                OZUIElement element = defaultSettingsContainer();
                element.addChild(defaultSettingsLabel(t().get("TC_LABEL_SHOP_LAYOUT", uiPlayer)));
                String currentValue = ShopPlayerPreferences.layout(uiPlayer);
                boolean listLayout = ShopPlayerPreferences.LAYOUT_LIST.equals(currentValue);
                element.addChild(switchButtons(uiPlayer, listLayout, event -> {
                    ShopPlayerPreferences.setLayout(uiPlayer,
                            listLayout ? ShopPlayerPreferences.LAYOUT_CARD : ShopPlayerPreferences.LAYOUT_LIST);
                    redrawContent();
                }, t().get("TC_BTN_LAYOUT_CARD", uiPlayer), t().get("TC_BTN_LAYOUT_LIST", uiPlayer)));
                return element;
            }
        };
    }
}
