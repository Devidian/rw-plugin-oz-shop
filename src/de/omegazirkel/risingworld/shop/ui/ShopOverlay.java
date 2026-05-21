package de.omegazirkel.risingworld.shop.ui;

import java.util.Arrays;
import java.util.List;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.shop.ShopZone;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.ButtonFactory;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.InfoButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

// TODO: refactor -> extend new BasePluginOverlayWithTabs from Tools to reduce code here
public class ShopOverlay extends OZUIElement {
    private static final int PANEL_HEIGHT = 560;
    private static final int BODY_HEIGHT = 360;
    private static final int TABLE_BODY_HEIGHT = 300;
    protected static String titleLabelKey = "TC_SHOP_UI_TITLE";
    protected static String descLabelKey = "TC_SHOP_UI_SUBTITLE";
    


    private final Shop plugin;
    private final Player player;
    private final I18n t;
    private final Colors c = Colors.getInstance();
    private OZUIElement panel;
    private OZUIElement body;
    private Tab activeTab = Tab.SYSTEM;
    private final WalletBridge walletBridge;

    private enum Tab {
        SYSTEM,
        PLUGIN,
        ADMIN
    }

    public ShopOverlay(Shop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.walletBridge = new WalletBridge(plugin);
        this.t = I18n.getInstance(plugin);
        setClickable(false);
        setPivot(Pivot.UpperLeft);
        setSize(100, 100, true);
        setBackgroundColor(0, 0, 0, 0.4f);
        rebuild();
    }

    private void rebuild() {
        removeAllChilds();
        panel = new OZUIElement();
        panel.setPivot(Pivot.MiddleCenter);
        panel.setPosition(50f, 50f, true);
        panel.style.width.set(88f, Unit.Percent);
        panel.style.height.set(PANEL_HEIGHT, Unit.Pixel);
        panel.setBackgroundColor(0, 0, 0, 0.88f);
        panel.setBorderColor(0.95f, 0.75f, 0.25f, 0.6f);
        panel.setBorder(1);
        panel.setBorderEdgeRadius(6, false);
        addChild(panel);

        setupHeader();
        setupTabs();
        setupBody();
    }

    private void setupHeader() {
        UILabel title = new UILabel(t.get(titleLabelKey, player));
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(24, 18, false);
        title.setFont(Font.DefaultBold);
        title.setFontSize(24);
        panel.addChild(title);

        UILabel subtitle = new UILabel(t.get(descLabelKey, player));
        subtitle.setPivot(Pivot.UpperLeft);
        subtitle.setPosition(24, 52, false);
        subtitle.setFont(Font.Default);
        subtitle.setFontSize(12);
        panel.addChild(subtitle);

        OZUIElement closeButton = new OZUIElement();
        closeButton.setPivot(Pivot.UpperRight);
        closeButton.style.position.set(Position.Absolute);
        closeButton.style.right.set(18, Unit.Pixel);
        closeButton.style.top.set(18, Unit.Pixel);
        closeButton.setSize(34, 34, false);
        closeButton.setBorder(1);
        closeButton.setBorderColor(0.95f, 0.75f, 0.25f, 0.54f);
        closeButton.setBorderEdgeRadius(4, false);
        closeButton.setBackgroundColor(0.12f, 0.10f, 0.08f, 0.9f);
        closeButton.setHoverBackgroundColor(0x611F1AF2);
        closeButton.setClickable(true);
        closeButton.setClickAction(event -> close());
        UILabel closeLabel = label("X", 18, Font.DefaultBold);
        closeLabel.setPivot(Pivot.MiddleCenter);
        closeLabel.setPosition(50, 50, true);
        closeLabel.setSize(100, 100, true);
        closeLabel.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.addChild(closeLabel);
        panel.addChild(closeButton);
    }

    private void setupTabs() {
        panel.addChild(tab(t.get("TC_SHOP_UI_TAB_SYSTEM", player), 24, 86, 170, Tab.SYSTEM));
        panel.addChild(tab(t.get("TC_SHOP_UI_TAB_PLUGIN", player), 194, 86, 170, Tab.PLUGIN));
        if (player.isAdmin()) {
            panel.addChild(tab(t.get("TC_SHOP_UI_TAB_ADMIN", player), 364, 86, 170, Tab.ADMIN));
        }
    }

    private OZUIElement tab(String text, float x, float y, float width, Tab tab) {
        OZUIElement button = new OZUIElement();
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(width, 38, false);
        button.setBorder(1);
        button.setBorderEdgeRadius(4, false);
        button.setClickable(true);
        button.setClickAction(event -> {
            activeTab = tab;
            rebuild();
        });
        if (activeTab == tab) {
            button.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.84f);
            button.setBorderColor(0.95f, 0.75f, 0.25f, 0.74f);
        } else {
            button.setBackgroundColor(0.10f, 0.10f, 0.10f, 0.38f);
            button.setBorderColor(0.95f, 0.75f, 0.25f, 0.24f);
        }
        UILabel label = label(text, 15, Font.DefaultBold);
        label.setPivot(Pivot.MiddleCenter);
        label.setPosition(50, 50, true);
        label.setSize(100, 100, true);
        label.setTextAlign(TextAnchor.MiddleCenter);
        button.addChild(label);
        return button;
    }

    private void setupBody() {
        body = new OZUIElement();
        body.setPivot(Pivot.UpperLeft);
        body.setPosition(24, 124, false);
        body.style.width.set(96, Unit.Percent);
        body.style.height.set(BODY_HEIGHT, Unit.Pixel);
        body.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
        body.setBorder(1);
        body.setBorderColor(0.95f, 0.75f, 0.25f, 0.48f);
        body.setBorderEdgeRadius(4, false);
        panel.addChild(body);

        if (activeTab == Tab.SYSTEM) {
            if (plugin.isSystemShopAvailableFor(player)) {
                setupOfferTable(plugin.listSystemOffers(), t.get("TC_SHOP_UI_EMPTY_SYSTEM", player));
            } else {
                setupOfferTable(List.of(), t.get("TC_SHOP_SYSTEM_DISABLED", player));
            }
        } else if (activeTab == Tab.PLUGIN) {
            setupOfferTable(plugin.listPluginOffers(), t.get("TC_SHOP_UI_EMPTY_PLUGIN", player));
        } else {
            setupAdminTable();
        }
    }

    private void setupOfferTable(List<ShopOffer> offers, String emptyText) {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t.get("TC_SHOP_UI_COL_OFFER", player),
                        t.get("TC_SHOP_UI_COL_PRICE", player),
                        t.get("TC_SHOP_UI_COL_SOURCE", player),
                        t.get("TC_SHOP_UI_COL_ACTION", player)),
                Arrays.asList(42f, 18f, 22f, 18f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<ShopOffer> enabled = offers.stream().filter(ShopOffer::isEnabled).toList();
        if (enabled.isEmpty()) {
            table.addRow(textOnlyRow(emptyText));
        } else {
            for (ShopOffer offer : enabled) {
                table.addRow(offerRow(offer));
            }
        }
        body.addChild(table);
    }

    private TableRow offerRow(ShopOffer offer) {
        String title = offer.getItemName().isBlank()
                ? offer.getTitle()
                : offer.getAmount() + "x " + offer.getItemName() + ":" + offer.getItemVariant();
        String currency = offer.getCurrencyIdentifier().isBlank()
                ? walletBridge.defaultCurrencyIdentifier()
                : offer.getCurrencyIdentifier();
                
        String price;
        try {
            price = offer.getPrice(player) + " " + currency;
        } catch (RuntimeException ex) {
            price = t.get("TC_SHOP_UI_PRICE_ERROR", player);
        }
        String source = offer.getSource().isBlank() ? offer.getPluginIdentifier() : offer.getSource();
        return new TableRow(Arrays.asList(
                labelCell(title, 42f),
                labelCell(price, 18f),
                labelCell(source, 22f),
                new TableCell(buyButton(offer), 18f)));
    }

    private UIElement buyButton(ShopOffer offer) {
        InfoButton button = ButtonFactory.info(t.get("TC_SHOP_UI_BUY", player), event -> {
            ShopPurchaseResult result = plugin.purchase(player, offer.getId());
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            rebuild();
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(82, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private void setupAdminTable() {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t.get("TC_SHOP_UI_COL_AREA", player),
                        t.get("TC_SHOP_UI_COL_AREA_ID", player),
                        t.get("TC_SHOP_UI_COL_SYSTEMSHOP", player),
                        t.get("TC_SHOP_UI_COL_CREATED_BY", player),
                        t.get("TC_SHOP_UI_COL_ACTION", player)),
                Arrays.asList(34f, 14f, 18f, 18f, 16f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<ShopZone> zones = plugin.listShopZones();
        if (zones.isEmpty()) {
            table.addRow(textOnlyRow(t.get("TC_SHOP_UI_EMPTY_AREAS", player)));
        } else {
            for (ShopZone zone : zones) {
                table.addRow(zoneRow(zone));
            }
        }
        body.addChild(table);
    }

    private TableRow zoneRow(ShopZone zone) {
        return new TableRow(Arrays.asList(
                labelCell(zone.getAreaName(), 34f),
                labelCell(String.valueOf(zone.getAreaId()), 14f),
                new TableCell(systemShopButton(zone), 18f),
                labelCell(zone.getCreatedBy(), 18f),
                new TableCell(removeZoneButton(zone), 16f)));
    }

    private UIElement systemShopButton(ShopZone zone) {
        InfoButton button = ButtonFactory.info(systemShopLabel(zone), event -> {
            int next = zone.getSystemShop() == -1 ? 0 : zone.getSystemShop() == 0 ? 1 : -1;
            ShopZone updated = plugin.setZoneSystemShop(zone.getAreaId(), next);
            if (updated != null) {
                player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_SYSTEMSHOP_UPDATED", player)
                        .replace("PH_AREA", updated.getAreaName())
                        .replace("PH_MODE", systemShopLabel(updated)));
            }
            rebuild();
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(92, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private String systemShopLabel(ShopZone zone) {
        return switch (zone.getSystemShop()) {
            case 0 -> t.get("TC_SHOP_UI_SYSTEMSHOP_DISABLED", player);
            case 1 -> t.get("TC_SHOP_UI_SYSTEMSHOP_ENABLED", player);
            default -> t.get("TC_SHOP_UI_SYSTEMSHOP_INHERIT", player);
        };
    }

    private UIElement removeZoneButton(ShopZone zone) {
        InfoButton button = ButtonFactory.info(t.get("TC_SHOP_UI_REMOVE", player), event -> {
            if (plugin.shopZoneService().deleteAreaZone(zone.getAreaId())) {
                player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_AREA_REMOVED", player)
                        .replace("PH_AREA", zone.getAreaName()));
            }
            rebuild();
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(82, 22, false);
        button.setBorderEdgeRadius(3, false);
        button.setBackgroundColor(0.78f, 0.16f, 0.12f, 1f);
        button.setHoverBackgroundColor(0xDD3228FF);
        return button;
    }

    private TableRow textOnlyRow(String text) {
        return new TableRow(Arrays.asList(labelCell(text, 100f)));
    }

    private TableCell labelCell(String text, float width) {
        UILabel label = label(text == null ? "" : text, 13, Font.Default);
        label.setTextWrap(false);
        return new TableCell(label, width);
    }

    private UILabel label(String text, int fontSize, Font font) {
        UILabel label = new UILabel(text == null ? "" : text);
        label.setFont(font);
        label.setFontSize(fontSize);
        label.setTextAlign(TextAnchor.MiddleLeft);
        return label;
    }

    private void close() {
        player.removeUIElement(this);
        player.deleteAttribute("oz.shop.ui.overlay");
        CursorManager.hide(player);
    }
}
