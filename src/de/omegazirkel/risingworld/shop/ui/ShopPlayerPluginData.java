package de.omegazirkel.risingworld.shop.ui;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopPlayerPreferences;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginDataPanel;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginData;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

import java.util.ArrayList;
import java.util.Arrays;

public class ShopPlayerPluginData extends PlayerPluginData {
    public ShopPlayerPluginData(String pluginVersion) {
        this.pluginLabel = Shop.name;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public BasePlayerPluginDataPanel createPlayerPluginDataUIElement(Player uiPlayer) {
        return new BasePlayerPluginDataPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                TableScrollView table = new TableScrollView(
                        Arrays.asList(
                                t().get("tc.data.col.description", uiPlayer),
                                "key",
                                "value"),
                        Arrays.asList(38f, 42f, 20f));
                table.setPosition(0, 0, false);
                table.style.width.set(100, Unit.Percent);
                table.setScrollBodyHeight(260);
                table.addRow(new TableRow(new ArrayList<>(Arrays.asList(
                        cell(t().get("tc.data.shop.layout", uiPlayer), 38f),
                        cell(ShopPlayerPreferences.LAYOUT_KEY, 42f),
                        cell(ShopPlayerPreferences.layout(uiPlayer), 20f)))));
                flexWrapper.addChild(table.getRoot());
            }

            private I18n t() {
                return I18n.getInstance(Shop.name);
            }

            private TableCell cell(String text, float width) {
                UILabel label = new UILabel(text == null ? "" : text);
                label.setFont(Font.Default);
                label.setFontSize(13);
                label.setTextWrap(false);
                label.setTextAlign(TextAnchor.MiddleLeft);
                return new TableCell(label, width);
            }
        };
    }
}
