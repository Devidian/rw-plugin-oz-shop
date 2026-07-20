package de.omegazirkel.risingworld.shop.ui;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.shop.ShopEconomyStore;
import de.omegazirkel.risingworld.shop.ShopPlayerPreferences;
import de.omegazirkel.risingworld.shop.ShopItemNames;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopOfferIcons;
import de.omegazirkel.risingworld.shop.ShopPurchaseResult;
import de.omegazirkel.risingworld.shop.ShopService;
import de.omegazirkel.risingworld.shop.ShopStockMode;
import de.omegazirkel.risingworld.shop.ShopZone;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.CursorManager;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Inventory.SlotType;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.style.Align;
import net.risingworld.api.ui.style.DisplayStyle;
import net.risingworld.api.ui.style.FlexDirection;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Justify;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.ui.style.Wrap;

// TODO: refactor -> extend new BasePluginOverlayWithTabs from Tools to reduce code here
public class ShopOverlay extends OZUIElement {
    private static final int PANEL_HEIGHT = 680;
    private static final int BODY_HEIGHT = 520;
    private static final int TABLE_BODY_HEIGHT = 420;
    private static final int SYSTEM_LIST_HEIGHT = 390;
    private static final int SYSTEM_OPTIONS_HEIGHT = BODY_HEIGHT - SYSTEM_LIST_HEIGHT - 1;
    private static final int SYSTEM_OPTIONS_Y = SYSTEM_LIST_HEIGHT + 0;
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
    private ShopZone pendingRemoveZone;
    private boolean pendingResetZoneStocks;
    private ShopOffer selectedSystemOffer;
    private OZUIElement systemOptions;
    private OZUIElement selectedSystemCard;
    private UILabel selectedSystemCardStockLabel;
    private UITextField selectedSystemAmountField;
    private UITextField zoneNameField;
    private UITextField zoneOfferFileField;
    private String zoneNameDraft = "";
    private String zoneOfferFileDraft = "";
    private ShopOffer selectedSystemEffectiveOffer;
    private UILabel selectedSystemBuyPreviewLabel;
    private UILabel selectedSystemSellPreviewLabel;
    private AdvancedButton selectedSystemBuyButton;
    private AdvancedButton selectedSystemSellButton;
    private int selectedSystemInventoryAmount;
    private String systemOfferFilter = "";

    private enum Tab {
        SYSTEM,
        PLUGIN,
        ZONE,
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
        if (activeTab == Tab.ZONE && (!player.isAdmin() || !plugin.isInValidArea(player))) {
            activeTab = player.isAdmin() ? Tab.ADMIN : Tab.SYSTEM;
        }
        selectedSystemAmountField = null;
        zoneNameField = null;
        zoneOfferFileField = null;
        selectedSystemEffectiveOffer = null;
        selectedSystemBuyPreviewLabel = null;
        selectedSystemSellPreviewLabel = null;
        selectedSystemBuyButton = null;
        selectedSystemSellButton = null;
        selectedSystemInventoryAmount = 0;
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
        setupWalletBalanceBar();
        setupBody();
        setupRemoveConfirmation();
        setupZoneStockResetConfirmation();
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
            if (plugin.isInValidArea(player)) {
                panel.addChild(tab(t.get("TC_SHOP_UI_TAB_ZONE", player), 364, 86, 170, Tab.ZONE));
                panel.addChild(tab(t.get("TC_SHOP_UI_TAB_ADMIN", player), 534, 86, 170, Tab.ADMIN));
            } else {
                panel.addChild(tab(t.get("TC_SHOP_UI_TAB_ADMIN", player), 364, 86, 170, Tab.ADMIN));
            }
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
            if (activeTab != Tab.SYSTEM) {
                selectedSystemOffer = null;
            }
            rebuild();
        });
        if (tab == Tab.ADMIN && activeTab == tab) {
            button.setBackgroundColor(0.19f, 0.10f, 0.03f, 0.92f);
            button.setBorderColor(1.0f, 0.48f, 0.12f, 0.86f);
        } else if (tab == Tab.ADMIN) {
            button.setBackgroundColor(0.16f, 0.07f, 0.03f, 0.58f);
            button.setBorderColor(1.0f, 0.48f, 0.12f, 0.42f);
        } else if (activeTab == tab) {
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
                setupSystemShop(plugin.listSystemOffers(player), t.get("TC_SHOP_UI_EMPTY_SYSTEM", player));
            } else {
                setupSystemShop(List.of(), t.get("TC_SHOP_SYSTEM_DISABLED", player));
            }
        } else if (activeTab == Tab.PLUGIN) {
            setupOffers(plugin.listPluginOffers(), t.get("TC_SHOP_UI_EMPTY_PLUGIN", player), OfferAction.BUY);
        } else if (activeTab == Tab.ZONE) {
            setupZoneTab();
        } else {
            setupAdminTable();
        }
    }

    private void setupWalletBalanceBar() {
        List<WalletBridge.CurrencyInfo> currencies = walletBridge.listCurrencies();
        if (currencies.isEmpty()) {
            return;
        }
        OZUIElement bar = new OZUIElement();
        bar.setPivot(Pivot.UpperLeft);
        bar.setPosition(0, -34, false);
        bar.style.width.set(100, Unit.Percent);
        bar.style.height.set(28, Unit.Pixel);
        bar.style.display.set(DisplayStyle.Flex);
        bar.style.flexDirection.set(FlexDirection.Row);
        bar.style.flexWrap.set(Wrap.NoWrap);
        bar.style.alignItems.set(Align.Center);
        bar.style.justifyContent.set(Justify.FlexStart);
        bar.setBackgroundColor(0, 0, 0, 0);

        boolean added = false;
        for (WalletBridge.CurrencyInfo currency : currencies) {
            WalletBridge.BalanceInfo balance = walletBridge.balance(player.getDbID(), currency.identifier());
            if (!balance.success()) {
                continue;
            }
            bar.addChild(walletBalanceEntry(currency, balance.balance()));
            added = true;
        }
        if (added) {
            panel.addChild(bar);
        }
    }

    private OZUIElement walletBalanceEntry(WalletBridge.CurrencyInfo currency, long balance) {
        OZUIElement entry = new OZUIElement();
        entry.setPivot(Pivot.UpperLeft);
        entry.style.width.set(104, Unit.Pixel);
        entry.style.height.set(24, Unit.Pixel);
        entry.style.marginLeft.set(8);
        entry.style.marginTop.set(2);
        entry.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
        entry.setBorder(1);
        entry.setBorderColor(0.95f, 0.75f, 0.25f, 0.48f);
        entry.setBorderEdgeRadius(4, false);

        TextureAsset iconAsset = currency.iconKey().isBlank() ? AssetManager.getIcon(player, "coin-default")
                : AssetManager.getIcon(player, currency.iconKey());
        OZUIElement icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.setPosition(6, 4, false);
        icon.setSize(16, 16, false);
        icon.setBackgroundColor(0, 0, 0, 0);
        if (iconAsset != null) {
            icon.style.backgroundImage.set(iconAsset);
            icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        }
        entry.addChild(icon);

        UILabel amount = label(String.valueOf(balance), 12, Font.DefaultBold);
        amount.setPivot(Pivot.UpperLeft);
        amount.setPosition(28, 3, false);
        amount.setSize(70, 18, false);
        amount.setFontColor(0xF2C766FF);
        amount.setTextAlign(TextAnchor.MiddleLeft);
        entry.addChild(amount);
        return entry;
    }

    private enum OfferAction {
        BUY,
        SELL
    }

    private void setupOffers(List<ShopOffer> offers, String emptyText, OfferAction action) {
        if (ShopPlayerPreferences.LAYOUT_LIST.equals(ShopPlayerPreferences.layout(player))) {
            setupOfferTable(offers, emptyText, action);
            return;
        }
        setupOfferCards(offers, emptyText, action);
    }

    private void setupOfferTable(List<ShopOffer> offers, String emptyText, OfferAction action) {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t.get("TC_SHOP_UI_COL_OFFER", player),
                        t.get("TC_SHOP_UI_COL_PRICE", player),
                        t.get("TC_SHOP_UI_COL_SOURCE", player),
                        t.get("TC_SHOP_UI_COL_ACTION", player)),
                Arrays.asList(42f, 18f, 22f, 18f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<ShopOffer> enabled = offers.stream().filter(offer -> visibleForAction(offer, action)).toList();
        if (enabled.isEmpty()) {
            table.addRow(textOnlyRow(emptyText));
        } else {
            for (ShopOffer offer : enabled) {
                table.addRow(offerRow(offer, action));
            }
        }
        body.addChild(table);
    }

    private void setupOfferCards(List<ShopOffer> offers, String emptyText, OfferAction action) {
        UIScrollView scroll = new UIScrollView(ScrollViewMode.Vertical);
        scroll.setPivot(Pivot.UpperLeft);
        scroll.setPosition(0, 0, false);
        scroll.style.width.set(100, Unit.Percent);
        scroll.style.height.set(BODY_HEIGHT, Unit.Pixel);
        scroll.style.paddingLeft.set(12);
        scroll.style.paddingRight.set(12);
        scroll.style.paddingTop.set(12);
        scroll.style.paddingBottom.set(18);

        OZUIElement wrapper = new OZUIElement();
        wrapper.setPivot(Pivot.UpperLeft);
        wrapper.style.width.set(100, Unit.Percent);
        wrapper.style.height.set(100, Unit.Percent);
        wrapper.style.display.set(DisplayStyle.Flex);
        wrapper.style.flexDirection.set(FlexDirection.Row);
        wrapper.style.flexWrap.set(Wrap.Wrap);
        wrapper.style.alignContent.set(Align.FlexStart);
        wrapper.style.justifyContent.set(Justify.FlexStart);
        scroll.addChild(wrapper);

        List<ShopOffer> enabled = offers.stream().filter(offer -> visibleForAction(offer, action)).toList();
        if (enabled.isEmpty()) {
            UILabel empty = label(emptyText, 15, Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(12, 12, false);
            empty.setSize(90, 40, true);
            empty.setTextWrap(true);
            wrapper.addChild(empty);
        } else {
            for (ShopOffer offer : enabled) {
                wrapper.addChild(offerCard(offer, action));
            }
        }
        body.addChild(scroll);
    }

    private void setupSystemShop(List<ShopOffer> offers, String emptyText) {
        List<ShopOffer> enabled = offers.stream()
                .filter(offer -> offer.isSystemOffer() && offer.isEnabled())
                .toList();
        if (selectedSystemOffer != null && enabled.stream()
                .noneMatch(offer -> offer.getId().equalsIgnoreCase(selectedSystemOffer.getId()))) {
            selectedSystemOffer = null;
        }
        List<ShopOffer> filtered = enabled.stream()
                .filter(this::matchesSystemOfferFilter)
                .toList();

        setupSystemSearch();
        UIScrollView scroll = new UIScrollView(ScrollViewMode.Vertical);
        scroll.setPivot(Pivot.UpperLeft);
        scroll.setPosition(0, 44, false);
        scroll.style.width.set(100, Unit.Percent);
        scroll.style.height.set(SYSTEM_LIST_HEIGHT - 44, Unit.Pixel);
        scroll.style.paddingLeft.set(12);
        scroll.style.paddingRight.set(12);
        scroll.style.paddingTop.set(6);
        scroll.style.paddingBottom.set(6);
        scroll.style.borderBottomWidth.set(1);
        scroll.style.borderTopWidth.set(1);
        scroll.setBorderColor(0.95f, 0.75f, 0.25f, 0.48f);

        OZUIElement wrapper = new OZUIElement();
        wrapper.setPivot(Pivot.UpperLeft);
        wrapper.style.width.set(100, Unit.Percent);
        wrapper.style.height.set(100, Unit.Percent);
        wrapper.style.display.set(DisplayStyle.Flex);
        wrapper.style.flexDirection.set(FlexDirection.Row);
        wrapper.style.flexWrap.set(Wrap.Wrap);
        wrapper.style.alignContent.set(Align.FlexStart);
        wrapper.style.justifyContent.set(Justify.FlexStart);
        scroll.addChild(wrapper);

        if (filtered.isEmpty()) {
            UILabel empty = label(enabled.isEmpty() ? emptyText : t.get("TC_SHOP_UI_EMPTY_FILTER", player), 15,
                    Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(12, 12, false);
            empty.setSize(90, 40, true);
            empty.setTextWrap(true);
            wrapper.addChild(empty);
        } else {
            for (ShopOffer offer : filtered) {
                wrapper.addChild(systemOfferCard(offer));
            }
        }
        body.addChild(scroll);
        setupSystemOptions(filtered);
    }

    private void setupSystemSearch() {
        UILabel label = label(t.get("TC_SHOP_UI_SEARCH", player), 14, Font.Default);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(12, 12, false);
        label.setSize(72, 26, false);
        body.addChild(label);

        UITextField searchField = textField(systemOfferFilter);
        searchField.setPivot(Pivot.UpperLeft);
        searchField.setPosition(84, 8, false);
        searchField.setSize(240, 30, false);
        searchField.setMaxCharacters(80);
        body.addChild(searchField);

        AdvancedButton apply = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_SEARCH_APPLY", player), event -> {
            searchField.getCurrentText(player, text -> {
                systemOfferFilter = text == null ? "" : text.trim();
                selectedSystemOffer = null;
                rebuild();
            });
        });
        apply.setPivot(Pivot.UpperLeft);
        apply.setPosition(336, 8, false);
        apply.setSize(92, 30, false);
        body.addChild(apply);

        AdvancedButton clear = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_SEARCH_CLEAR", player), event -> {
            systemOfferFilter = "";
            selectedSystemOffer = null;
            rebuild();
        });
        clear.setPivot(Pivot.UpperLeft);
        clear.setPosition(440, 8, false);
        clear.setSize(92, 30, false);
        body.addChild(clear);
    }

    private OZUIElement systemOfferCard(ShopOffer offer) {
        ShopOffer displayOffer = plugin.configuredSystemOffer(player, offer);
        boolean selected = selectedSystemOffer != null && offer.getId().equalsIgnoreCase(selectedSystemOffer.getId());
        OZUIElement card = new OZUIElement();
        card.setPivot(Pivot.UpperLeft);
        card.style.width.set(248, Unit.Pixel);
        card.style.height.set(168, Unit.Pixel);
        card.style.marginLeft.set(6);
        card.style.marginRight.set(6);
        card.style.marginTop.set(6);
        card.style.marginBottom.set(10);
        card.setPadding(12);
        card.setBackgroundColor(selected ? 0.18f : 0.10f, selected ? 0.15f : 0.09f, selected ? 0.10f : 0.08f, 0.94f);
        card.setBorder(selected ? 2 : 1);
        card.setBorderColor(0.95f, 0.75f, 0.25f, selected ? 0.9f : 0.42f);
        card.setBorderEdgeRadius(6, false);
        card.setClickable(true);

        OZUIElement icon = offerIcon(displayOffer);
        icon.setPosition(12, 54, false);
        card.addChild(icon);

        UILabel title = label(offerTitle(displayOffer), 15, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(12, 12, false);
        title.setSize(220, 38, false);
        title.setTextWrap(true);
        title.setTextAlign(TextAnchor.UpperLeft);
        card.addChild(title);

        UILabel stock = label(t.get("TC_SHOP_UI_STOCK", player)
                .replace("PH_STOCK", stockLabel(displayOffer, plugin.economyStateFor(player, displayOffer))), 12, Font.Default);
        stock.setPivot(Pivot.UpperLeft);
        stock.setPosition(58, 54, false);
        stock.setSize(172, 20, false);
        stock.setFontColor(0xC8C0B2FF);
        card.addChild(stock);

        UILabel mode = label(stockModeLabel(displayOffer.getStockMode()), 11, Font.Default);
        mode.setPivot(Pivot.UpperLeft);
        mode.setPosition(58, 72, false);
        mode.setSize(172, 18, false);
        mode.setFontColor(0xAFA89AFF);
        card.addChild(mode);

        if (displayOffer.usesPlayerSellLimits()) {
            UILabel limits = label(sellLimitLabel(displayOffer), 11, Font.Default);
            limits.setPivot(Pivot.UpperLeft);
            limits.setPosition(58, 90, false);
            limits.setSize(172, 18, false);
            limits.setFontColor(0xAFA89AFF);
            card.addChild(limits);
        }

        int priceY = 116;
        if (displayOffer.canPlayerBuyFromSystem()) {
            UILabel buyPrice = label(t.get("TC_SHOP_UI_BUY_PRICE", player)
                    .replace("PH_PRICE", offerUnitPrice(displayOffer, OfferAction.BUY)), 13, Font.DefaultBold);
            buyPrice.setPivot(Pivot.UpperLeft);
            buyPrice.setPosition(12, priceY, false);
            buyPrice.setSize(220, 22, false);
            buyPrice.setFontColor(0xF2C766FF);
            card.addChild(buyPrice);
            priceY += 24;
        }

        if (displayOffer.canPlayerSellToSystem()) {
            UILabel sellPrice = label(t.get("TC_SHOP_UI_SELL_PRICE", player)
                    .replace("PH_PRICE", offerUnitPrice(displayOffer, OfferAction.SELL)), 13, Font.DefaultBold);
            sellPrice.setPivot(Pivot.UpperLeft);
            sellPrice.setPosition(12, priceY, false);
            sellPrice.setSize(220, 22, false);
            sellPrice.setFontColor(0x9FE2A0FF);
            card.addChild(sellPrice);
        }

        card.setClickAction(event -> {
            if (selectedSystemCard != null && selectedSystemCard != card) {
                applySystemCardStyle(selectedSystemCard, false);
            }
            selectedSystemOffer = offer;
            selectedSystemCard = card;
            selectedSystemCardStockLabel = stock;
            applySystemCardStyle(card, true);
            refreshSystemOptions();
        });
        if (selected) {
            selectedSystemCard = card;
            selectedSystemCardStockLabel = stock;
        }
        return card;
    }

    private void setupSystemOptions(List<ShopOffer> enabled) {
        systemOptions = systemOptionsPanel();
        body.addChild(systemOptions);
        renderSystemOptions(systemOptions, enabled);
    }

    private OZUIElement systemOptionsPanel() {
        OZUIElement options = new OZUIElement();
        options.setPivot(Pivot.UpperLeft);
        options.setPosition(0, SYSTEM_OPTIONS_Y, false);
        options.style.width.set(100, Unit.Percent);
        options.style.height.set(SYSTEM_OPTIONS_HEIGHT, Unit.Pixel);
        options.setPadding(12);
        options.setBackgroundColor(0.06f, 0.06f, 0.05f, 0.92f);
        // options.setBorder(1);
        // options.setBorderColor(0.95f, 0.75f, 0.25f, 0.34f);
        // options.setBorderEdgeRadius(4, false);
        options.style.borderBottomLeftRadius.set(4, Unit.Pixel);
        options.style.borderBottomRightRadius.set(4, Unit.Pixel);
        options.updateStyle();
        return options;
    }

    private void refreshSystemOptions() {
        if (systemOptions == null || body == null) {
            rebuild();
            return;
        }
        systemOptions.removeAllChilds();
        List<ShopOffer> enabled = plugin.listSystemOffers(player).stream()
                .filter(offer -> offer.isSystemOffer() && offer.isEnabled())
                .filter(this::matchesSystemOfferFilter)
                .toList();
        renderSystemOptions(systemOptions, enabled);
    }

    private void renderSystemOptions(OZUIElement options, List<ShopOffer> enabled) {
        ShopOffer offer = selectedSystemOffer == null ? null
                : enabled.stream()
                        .filter(candidate -> candidate.getId().equalsIgnoreCase(selectedSystemOffer.getId()))
                        .findFirst()
                        .orElse(null);
        if (offer == null) {
            selectedSystemAmountField = null;
            selectedSystemEffectiveOffer = null;
            selectedSystemBuyPreviewLabel = null;
            selectedSystemSellPreviewLabel = null;
            selectedSystemBuyButton = null;
            selectedSystemSellButton = null;
            selectedSystemInventoryAmount = 0;
            UILabel empty = label(t.get("TC_SHOP_UI_OPTIONS_EMPTY", player), 14, Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(12, 12, false);
            empty.setSize(90, 26, true);
            options.addChild(empty);
            return;
        }
        ShopOffer effectiveOffer = plugin.configuredSystemOffer(player, offer);
        selectedSystemBuyButton = null;
        selectedSystemSellButton = null;

        UILabel title = label(offerTitle(effectiveOffer), 16, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(12, 10, false);
        title.setSize(260, 24, false);
        options.addChild(title);

        UILabel stock = label(t.get("TC_SHOP_UI_STOCK", player)
                .replace("PH_STOCK", stockLabel(effectiveOffer, plugin.economyStateFor(player, effectiveOffer))), 12, Font.Default);
        stock.setPivot(Pivot.UpperLeft);
        stock.setPosition(12, 36, false);
        stock.setSize(260, 20, false);
        stock.setFontColor(0xC8C0B2FF);
        options.addChild(stock);

        UILabel mode = label(t.get("TC_SHOP_UI_STOCK_MODE", player)
                .replace("PH_MODE", stockModeLabel(effectiveOffer.getStockMode())), 11, Font.Default);
        mode.setPivot(Pivot.UpperLeft);
        mode.setPosition(12, 56, false);
        mode.setSize(260, 18, false);
        mode.setFontColor(0xAFA89AFF);
        options.addChild(mode);

        if (effectiveOffer.usesPlayerSellLimits() || player.isAdmin()) {
            UILabel limits = label(t.get("TC_SHOP_UI_SELL_LIMITS", player)
                    .replace("PH_LIMITS", sellLimitLabel(effectiveOffer)), 11, Font.Default);
            limits.setPivot(Pivot.UpperLeft);
            limits.setPosition(12, 76, false);
            limits.setSize(260, 18, false);
            limits.setFontColor(0xAFA89AFF);
            options.addChild(limits);
        }

        UILabel amountLabel = label(t.get("TC_SHOP_UI_AMOUNT", player), 13, Font.Default);
        amountLabel.setPivot(Pivot.UpperLeft);
        amountLabel.setPosition(292, 16, false);
        amountLabel.setSize(72, 24, false);
        options.addChild(amountLabel);

        UITextField amountField = textField(String.valueOf(effectiveOffer.getAmount()));
        amountField.setPivot(Pivot.UpperLeft);
        amountField.setPosition(360, 12, false);
        amountField.setSize(92, 30, false);
        options.addChild(amountField);
        selectedSystemAmountField = amountField;
        selectedSystemEffectiveOffer = effectiveOffer;

        String buyDisabled = plugin.systemBuyDisabledReason(player, effectiveOffer, 1);
        String sellDisabled = plugin.systemSellDisabledReason(player, effectiveOffer, 1);
        String statusText = economyDisabledLabel(buyDisabled, sellDisabled);
        if (!statusText.isBlank()) {
            UILabel status = label(statusText, 11, Font.Default);
            status.setPivot(Pivot.UpperLeft);
            status.setPosition(292, 82, false);
            status.setSize(440, 18, false);
            status.setFontColor(0xD89272FF);
            status.setTextWrap(true);
            options.addChild(status);
        }

        // a container for buy and sale buttons
        OZUIElement optionActions = new OZUIElement();
        optionActions.setPivot(Pivot.UpperLeft);
        optionActions.setAbsolute();
        optionActions.setPosition(470, 0, false);
        optionActions.style.width.set(116, Unit.Pixel);
        optionActions.style.height.set(100, Unit.Percent);
        options.addChild(optionActions);

        if (effectiveOffer.canPlayerBuyFromSystem()) {
            AdvancedButton buy = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_BUY_SELECTED", player),
                    event -> executeSystemAction(effectiveOffer, amountField, false));
            buy.setPivot(Pivot.UpperLeft);
            buy.setPosition(0, 12, false);
            buy.setSize(116, 30, false);
            buy.setBorder(1);
            buy.setBorderEdgeRadius(3, false);
            selectedSystemBuyButton = buy;
            updateSelectedSystemBuyButton(String.valueOf(effectiveOffer.getAmount()));
            optionActions.addChild(buy);
        }

        int inventoryAmount = inventoryAmount(effectiveOffer);
        selectedSystemInventoryAmount = inventoryAmount;
        UILabel inventory = label(t.get("TC_SHOP_UI_INVENTORY", player)
                .replace("PH_AMOUNT", String.valueOf(inventoryAmount)), 12, Font.Default);
        inventory.setPivot(Pivot.UpperLeft);
        inventory.setPosition(292, 52, false);
        inventory.setSize(160, 22, false);
        inventory.setFontColor(0xC8C0B2FF);
        options.addChild(inventory);

        selectedSystemBuyPreviewLabel = label(
                systemBuyPreviewText(effectiveOffer, String.valueOf(effectiveOffer.getAmount())),
                12, Font.DefaultBold);
        selectedSystemBuyPreviewLabel.setPivot(Pivot.UpperLeft);
        selectedSystemBuyPreviewLabel.setPosition(592, 17, false);
        selectedSystemBuyPreviewLabel.setSize(160, 20, false);
        selectedSystemBuyPreviewLabel.setFontColor(0xF2C766FF);
        options.addChild(selectedSystemBuyPreviewLabel);

        selectedSystemSellPreviewLabel = label(
                systemSellPreviewText(effectiveOffer, String.valueOf(effectiveOffer.getAmount())),
                12, Font.DefaultBold);
        selectedSystemSellPreviewLabel.setPivot(Pivot.UpperLeft);
        selectedSystemSellPreviewLabel.setPosition(592, 57, false);
        selectedSystemSellPreviewLabel.setSize(160, 20, false);
        selectedSystemSellPreviewLabel.setFontColor(0x9FE2A0FF);
        options.addChild(selectedSystemSellPreviewLabel);

        if (effectiveOffer.canPlayerSellToSystem()) {
            AdvancedButton sell = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_SELL_SELECTED", player),
                    event -> executeSystemAction(effectiveOffer, amountField, true));

            selectedSystemSellButton = sell;
            updateSelectedSystemSellButton(String.valueOf(effectiveOffer.getAmount()));
            optionActions.addChild(sell);
        }

        if (player.isAdmin()) {
            addAdminEconomyDetails(options, effectiveOffer, plugin.economyStateFor(player, effectiveOffer));
        }
    }

    private void addAdminEconomyDetails(OZUIElement options, ShopOffer offer, ShopEconomyStore.EconomyState state) {
        int x = 784;
        UILabel heading = label(t.get("TC_SHOP_UI_ADMIN_ECONOMY_TITLE", player), 12, Font.DefaultBold);
        heading.setPivot(Pivot.UpperLeft);
        heading.setPosition(x, 6, false);
        heading.setSize(260, 18, false);
        heading.setFontColor(0xF2C766FF);
        options.addChild(heading);

        addAdminEconomyLine(options, x, 28, t.get("TC_SHOP_UI_ADMIN_STOCKS", player)
                .replace("PH_STOCK", String.valueOf(state.stock()))
                .replace("PH_TARGET", String.valueOf(state.targetStock()))
                .replace("PH_LIMIT", String.valueOf(state.stockLimit())));
        addAdminEconomyLine(options, x, 46, t.get("TC_SHOP_UI_ADMIN_MODE", player)
                .replace("PH_MODE", stockModeLabel(offer.getStockMode())));
        addAdminEconomyLine(options, x, 64, t.get("TC_SHOP_UI_ADMIN_DRAIN", player)
                .replace("PH_PERCENT", String.valueOf(offer.getDrainPercent()))
                .replace("PH_MAX", String.valueOf(offer.getDrainMax())));
        addAdminEconomyLine(options, x, 82, t.get("TC_SHOP_UI_ADMIN_RESTOCK", player)
                .replace("PH_PERCENT", String.valueOf(offer.getRestockPercent()))
                .replace("PH_MAX", String.valueOf(offer.getRestockMax())));
        addAdminEconomyLine(options, x, 100, adminNextTickLabel(plugin.economyTickStatusFor(player, offer)));

        AdvancedButton reset = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_ADMIN_RESET_TARGET", player),
                event -> showResetStockConfirmation(offer, state));
        reset.setPivot(Pivot.UpperLeft);
        reset.setPosition(x + 286, 42, false);
        reset.setSize(180, 28, false);
        reset.setBorderEdgeRadius(3, false);
        styleResetButton(reset);
        options.addChild(reset);
    }

    private void addAdminEconomyLine(OZUIElement options, int x, int y, String text) {
        UILabel label = label(text, 11, Font.Default);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(x, y, false);
        label.setSize(280, 16, false);
        label.setFontColor(0xC8C0B2FF);
        options.addChild(label);
    }

    private void showResetStockConfirmation(ShopOffer offer, ShopEconomyStore.EconomyState state) {
        OZUIElement blocker = new OZUIElement();
        blocker.setPivot(Pivot.UpperLeft);
        blocker.setPosition(0, 0, true);
        blocker.setSize(100, 100, true);
        blocker.setBackgroundColor(0, 0, 0, 0.54f);
        blocker.setClickable(true);

        OZUIElement dialog = new OZUIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(440, 210, false);
        dialog.setBackgroundColor(0.08f, 0.07f, 0.06f, 0.98f);
        dialog.setBorder(1);
        dialog.setBorderColor(0.95f, 0.75f, 0.25f, 0.74f);
        dialog.setBorderEdgeRadius(6, false);

        UILabel title = label(t.get("TC_SHOP_UI_ADMIN_RESET_CONFIRM_TITLE", player), 20, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 16, false);
        title.setSize(400, 28, false);
        dialog.addChild(title);

        UILabel text = label(t.get("TC_SHOP_UI_ADMIN_RESET_CONFIRM_TEXT", player)
                .replace("PH_OFFER", offerTitle(offer))
                .replace("PH_STOCK", String.valueOf(state.stock()))
                .replace("PH_TARGET", String.valueOf(state.targetStock())), 14, Font.Default);
        text.setPivot(Pivot.UpperLeft);
        text.setPosition(18, 56, false);
        text.setSize(400, 72, false);
        text.setTextWrap(true);
        text.setTextAlign(TextAnchor.UpperLeft);
        dialog.addChild(text);

        UIElement cancel = AdvancedButtonFactory.cancel(t.get("TC_BTN_CANCEL", player), event -> panel.removeChild(blocker));
        cancel.setPivot(Pivot.LowerLeft);
        cancel.setPosition(18, 192, false);
        cancel.setSize(150, 30, false);
        dialog.addChild(cancel);

        UIElement confirm = AdvancedButtonFactory.ok(t.get("TC_SHOP_UI_ADMIN_RESET_TARGET", player), event -> {
            panel.removeChild(blocker);
            ShopPurchaseResult result = plugin.resetSystemOfferStockToTarget(player, offer);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            refreshSystemOptions();
        });
        confirm.setPivot(Pivot.LowerRight);
        confirm.setPosition(422, 192, false);
        confirm.setSize(190, 30, false);
        dialog.addChild(confirm);

        blocker.addChild(dialog);
        panel.addChild(blocker);
    }

    private boolean visibleForAction(ShopOffer offer, OfferAction action) {
        return action == OfferAction.SELL ? offer.isSystemOffer() && offer.canPlayerSellToSystem() : offer.isEnabled();
    }

    private TableRow offerRow(ShopOffer offer, OfferAction action) {
        return new TableRow(Arrays.asList(
                labelCell(offerTitle(offer), 42f),
                labelCell(offerPrice(offer, action), 18f),
                labelCell(offerSource(offer), 22f),
                new TableCell(actionButton(offer, action), 18f)));
    }

    private OZUIElement offerCard(ShopOffer offer, OfferAction action) {
        OZUIElement card = new OZUIElement();
        card.setPivot(Pivot.UpperLeft);
        card.style.width.set(260, Unit.Pixel);
        card.style.height.set(158, Unit.Pixel);
        card.style.marginLeft.set(6);
        card.style.marginRight.set(6);
        card.style.marginTop.set(6);
        card.style.marginBottom.set(10);
        card.setPadding(12);
        card.setBackgroundColor(0.10f, 0.09f, 0.08f, 0.92f);
        card.setBorder(1);
        card.setBorderColor(0.95f, 0.75f, 0.25f, 0.42f);
        card.setBorderEdgeRadius(6, false);

        OZUIElement icon = offerIcon(offer);
        card.addChild(icon);

        UILabel title = label(offerTitle(offer), 15, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(58, 12, false);
        title.setSize(185, 36, false);
        title.setTextWrap(true);
        title.setTextAlign(TextAnchor.UpperLeft);
        card.addChild(title);

        UILabel source = label(offerSource(offer), 12, Font.Default);
        source.setPivot(Pivot.UpperLeft);
        source.setPosition(58, 50, false);
        source.setSize(185, 22, false);
        source.setFontColor(0xC8C0B2FF);
        card.addChild(source);

        UILabel description = label(offer.getDescription(player), 12, Font.Default);
        description.setPivot(Pivot.UpperLeft);
        description.setPosition(12, 76, false);
        description.setSize(232, 34, false);
        description.setFontColor(0xD8D0C0FF);
        description.setTextWrap(true);
        description.setTextAlign(TextAnchor.UpperLeft);
        card.addChild(description);

        UILabel price = label(offerPrice(offer, action), 14, Font.DefaultBold);
        price.setPivot(Pivot.LowerLeft);
        price.setPosition(12, 148, false);
        price.setSize(128, 26, false);
        price.setFontColor(0xF2C766FF);
        card.addChild(price);

        UIElement buy = actionButton(offer, action);
        buy.setPivot(Pivot.LowerRight);
        buy.setPosition(246, 148, false);
        buy.setSize(88, 26, false);
        card.addChild(buy);

        return card;
    }

    private OZUIElement offerIcon(ShopOffer offer) {
        TextureAsset asset = ShopOfferIcons.resolve(player, offer);

        OZUIElement icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.setPosition(12, 12, false);
        icon.setSize(36, 36, false);
        icon.setBackgroundColor(0, 0, 0, 0);
        if (asset != null) {
            icon.style.backgroundImage.set(asset);
            icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        }
        return icon;
    }

    private String offerTitle(ShopOffer offer) {
        if (offer.getItemName().isBlank()) {
            return offer.getTitle(player);
        }
        return ShopItemNames.label(offer.getItemName(), offer.getItemVariant(), offer.getTitle(player));
    }

    private String offerPrice(ShopOffer offer, OfferAction action) {
        ShopOffer pricedOffer = offer.isSystemOffer() ? plugin.dynamicEconomyOffer(player, offer, 1) : offer;
        String currency = currencyIdentifier(offer);
        if (action == OfferAction.SELL) {
            if (offer.isSystemOffer()) {
                ShopService.SellQuote quote = plugin.sellQuote(player, offer, 1);
                return (quote.sellable() ? quote.payout() : 0L) + " " + currency;
            }
            return pricedOffer.getBuyPrice() + " " + currency;
        }
        try {
            return pricedOffer.getPrice(player) + " " + currency;
        } catch (RuntimeException ex) {
            return t.get("TC_SHOP_UI_PRICE_ERROR", player);
        }
    }

    private String offerUnitPrice(ShopOffer offer, OfferAction action) {
        ShopOffer pricedOffer = offer.isSystemOffer() ? plugin.dynamicEconomyOffer(player, offer, 1) : offer;
        double units = Math.max(1, pricedOffer.getAmount());
        try {
            if (offer.isSystemOffer() && action == OfferAction.SELL) {
                ShopService.SellQuote quote = plugin.sellQuote(player, offer, 1);
                double price = quote.sellable() ? quote.payout() / (double) quote.amount() : 0.0d;
                return formatUnitAmount(price) + " " + currencyIdentifier(offer)
                        + t.get("TC_SHOP_UI_UNIT_PRICE_SUFFIX", player);
            }
            double price = offer.isSystemOffer()
                    ? systemUnitPrice(pricedOffer, action)
                    : (action == OfferAction.SELL
                            ? pricedOffer.getBuyPrice() / units
                            : pricedOffer.getPrice(player) / units);
            return formatUnitAmount(Math.max(0.0d, price)) + " " + currencyIdentifier(offer)
                    + t.get("TC_SHOP_UI_UNIT_PRICE_SUFFIX", player);
        } catch (RuntimeException ex) {
            return t.get("TC_SHOP_UI_PRICE_ERROR", player);
        }
    }

    private static double systemUnitPrice(ShopOffer pricedOffer, OfferAction action) {
        double spread = Math.max(1.0d, pricedOffer.getSpreadPercent()) / 100.0d;
        double factor = action == OfferAction.SELL
                ? Math.max(0.0d, 1.0d - (spread / 2.0d))
                : 1.0d + (spread / 2.0d);
        return pricedOffer.getBasePrice() * factor;
    }

    private String formatUnitAmount(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(playerLocale());
        format.setMinimumFractionDigits(3);
        format.setMaximumFractionDigits(3);
        format.setGroupingUsed(false);
        return format.format(amount);
    }

    private Locale playerLocale() {
        String language = player.getSystemLanguage();
        if (language == null || language.isBlank()) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(language.trim().replace('_', '-'));
    }

    private String currencyIdentifier(ShopOffer offer) {
        if (offer == null || offer.getCurrencyIdentifier().isBlank()) {
            return walletBridge.defaultCurrencyIdentifier();
        }
        return offer.getCurrencyIdentifier();
    }

    private String offerSource(ShopOffer offer) {
        return offer.getSource().isBlank() ? offer.getPluginIdentifier() : offer.getSource();
    }

    private UIElement actionButton(ShopOffer offer, OfferAction action) {
        AdvancedButton button = AdvancedButtonFactory
                .defaultButton(t.get(action == OfferAction.SELL ? "TC_SHOP_UI_SELL" : "TC_SHOP_UI_BUY", player), event -> {
                    ShopPurchaseResult result = action == OfferAction.SELL
                            ? plugin.sell(player, offer.getId(), 1)
                            : plugin.purchase(player, offer.getId());
                    player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
                    rebuild();
                });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(82, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private void executeSystemAction(ShopOffer offer, UITextField amountField, boolean sellToSystem) {
        amountField.getCurrentText(player, amountText -> {
            int amount = parseStrictPositiveInt(amountText);
            int quantity = quantityForAmount(offer, amountText);
            if (amount <= 0 || quantity <= 0) {
                refreshSystemOptions();
                return;
            }
            if (!sellToSystem && !canBuySelectedSystemAmount(offer, amountText)) {
                refreshSystemOptions();
                return;
            }
            if (sellToSystem && requiredSystemSellAmount(offer, amountText) > inventoryAmount(offer)) {
                player.sendTextMessage(c.warning + t.get("TC_SHOP_UI_NOT_IN_INVENTORY", player));
                refreshSystemOptions();
                return;
            }
            ShopPurchaseResult result = sellToSystem
                    ? plugin.sell(player, offer.getId(), quantity)
                    : plugin.purchase(player, offer.getId(), quantity);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            refreshSelectedSystemTradeState(offer);
        });
    }

    private void refreshSelectedSystemTradeState(ShopOffer offer) {
        ShopOffer effectiveOffer = plugin.configuredSystemOffer(player, offer);
        if (selectedSystemCardStockLabel != null) {
            selectedSystemCardStockLabel.setText(t.get("TC_SHOP_UI_STOCK", player)
                    .replace("PH_STOCK", stockLabel(effectiveOffer, plugin.economyStateFor(player, effectiveOffer))));
        }
        refreshSystemOptions();
    }

    public void onAmountFieldChanged(UITextField field, String newText) {
        if (field == null || field != selectedSystemAmountField || selectedSystemEffectiveOffer == null) {
            if (field != null && field == zoneNameField) {
                zoneNameDraft = newText == null ? "" : newText.trim();
            }
            if (field != null && field == zoneOfferFileField) {
                zoneOfferFileDraft = newText == null ? "" : newText.trim();
            }
            return;
        }
        if (selectedSystemBuyPreviewLabel != null) {
            selectedSystemBuyPreviewLabel.setText(systemBuyPreviewText(selectedSystemEffectiveOffer, newText));
        }
        if (selectedSystemSellPreviewLabel != null) {
            selectedSystemSellPreviewLabel.setText(systemSellPreviewText(selectedSystemEffectiveOffer, newText));
        }
        updateSelectedSystemBuyButton(newText);
        updateSelectedSystemSellButton(newText);
    }

    private int inventoryAmount(ShopOffer offer) {
        if (offer == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int amount = 0;
        for (SlotType slotType : SlotType.values()) {
            int slots = inventory.getSlotCount(slotType);
            for (int slot = 0; slot < slots; slot++) {
                Item item = inventory.getItem(slot, slotType);
                if (ShopItemNames.matches(item, offer.getItemName(), offer.getItemVariant(), offer.getItemTypeId())) {
                    amount += Math.max(0, item.getStack());
                }
            }
        }
        return amount;
    }

    private String stockLabel(ShopOffer offer, ShopEconomyStore.EconomyState state) {
        if (offer != null && offer.getStockMode() == ShopStockMode.STATIC) {
            return t.get("TC_SHOP_UI_STOCK_UNLIMITED", player);
        }
        if (state == null || !state.limited()) {
            return t.get("TC_SHOP_UI_STOCK_UNLIMITED", player);
        }
        return state.stock() + " / " + state.stockLimit();
    }

    private String adminNextTickLabel(ShopEconomyStore.EconomyTickStatus status) {
        if (status == null || !status.active()) {
            return t.get("TC_SHOP_UI_ADMIN_NEXT_TICK", player)
                    .replace("PH_DRAIN", t.get("TC_SHOP_UI_TICK_INACTIVE", player))
                    .replace("PH_RESTOCK", t.get("TC_SHOP_UI_TICK_INACTIVE", player));
        }
        return t.get("TC_SHOP_UI_ADMIN_NEXT_TICK", player)
                .replace("PH_DRAIN", tickLabel(status.nextDrainAt()))
                .replace("PH_RESTOCK", tickLabel(status.nextRestockAt()));
    }

    private String tickLabel(long timestamp) {
        if (timestamp <= 0L) {
            return t.get("TC_SHOP_UI_TICK_INACTIVE", player);
        }
        long remainingMillis = timestamp - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return t.get("TC_SHOP_UI_TICK_NOW", player);
        }
        return t.get("TC_SHOP_UI_TICK_IN", player)
                .replace("PH_TIME", durationLabel(remainingMillis));
    }

    private static String durationLabel(long millis) {
        long totalSeconds = Math.max(1L, (long) Math.ceil(millis / 1000.0d));
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private String systemSellPreviewText(ShopOffer offer, String amountText) {
        if (offer == null || !offer.canPlayerSellToSystem()) {
            return "";
        }
        int quantity = quantityForAmount(offer, amountText);
        ShopService.SellQuote quote = plugin.sellQuote(player, offer, quantity);
        String currency = currencyIdentifier(offer);
        return t.get("TC_SHOP_UI_SELL_PREVIEW", player)
                .replace("PH_PRICE", quantity <= 0 || !quote.sellable()
                        ? "0 " + currency
                        : quote.payout() + " " + currency);
    }

    private String systemBuyPreviewText(ShopOffer offer, String amountText) {
        if (offer == null || !offer.canPlayerBuyFromSystem()) {
            return "";
        }
        ShopOffer pricedOffer = plugin.dynamicEconomyOffer(player, offer, quantityForAmount(offer, amountText));
        String currency = currencyIdentifier(offer);
        try {
            return t.get("TC_SHOP_UI_BUY_PREVIEW", player)
                    .replace("PH_PRICE", quantityForAmount(offer, amountText) <= 0
                            ? "0 " + currency
                            : pricedOffer.getPrice(player) + " " + currency);
        } catch (RuntimeException ex) {
            return t.get("TC_SHOP_UI_PRICE_ERROR", player);
        }
    }

    private void updateSelectedSystemBuyButton(String amountText) {
        if (selectedSystemBuyButton == null || selectedSystemEffectiveOffer == null) {
            return;
        }
        boolean enabled = canBuySelectedSystemAmount(selectedSystemEffectiveOffer, amountText);
        selectedSystemBuyButton.setPivot(Pivot.UpperLeft);
        selectedSystemBuyButton.setPosition(0, 12, false);
        selectedSystemBuyButton.setSize(116, 30, false);
        selectedSystemBuyButton.setBorder(1);
        selectedSystemBuyButton.setBorderEdgeRadius(3, false);
        if (enabled) {
            styleBuyButton(selectedSystemBuyButton);
            selectedSystemBuyButton.setClickable(true);
        } else {
            styleDisabledButton(selectedSystemBuyButton);
            selectedSystemBuyButton.setClickable(false);
        }
        refreshButtonParent(selectedSystemBuyButton);
    }

    private void updateSelectedSystemSellButton(String amountText) {
        if (selectedSystemSellButton == null || selectedSystemEffectiveOffer == null) {
            return;
        }
        int quantity = quantityForAmount(selectedSystemEffectiveOffer, amountText);
        boolean enabled = quantity > 0
                && requiredSystemSellAmount(selectedSystemEffectiveOffer, amountText) <= selectedSystemInventoryAmount
                && plugin.systemSellDisabledReason(player, selectedSystemEffectiveOffer, quantity).isBlank();
        selectedSystemSellButton.setPivot(Pivot.UpperLeft);
        selectedSystemSellButton.setPosition(0, 52, false);
        selectedSystemSellButton.setSize(116, 30, false);
        selectedSystemSellButton.setBorder(1);
        selectedSystemSellButton.setBorderEdgeRadius(3, false);
        if (enabled) {
            styleSellButton(selectedSystemSellButton);
            selectedSystemSellButton.setClickable(true);
        } else {
            styleDisabledButton(selectedSystemSellButton);
            selectedSystemSellButton.setClickable(false);
        }
        refreshButtonParent(selectedSystemSellButton);
    }

    private void refreshButtonParent(AdvancedButton button) {
        // workaround, remove and readd button because styling is broken after switching
        // from enabled to disabled and back
        UIElement parent = button.getParent();
        if (parent != null) {
            parent.removeChild(button);
            parent.addChild(button);
        }
    }

    private void styleBuyButton(AdvancedButton button) {
        button.setBackgroundColor(0.54f, 0.38f, 0.07f, 0.96f);
        button.setBorderColor(0.95f, 0.75f, 0.25f, 0.62f);
        button.setHoverBackgroundColor(0xA07016F5);
        button.setHoverBorderColor(0xF2C766DD);
        button.setHoverBorderWidth(1);
    }

    private void styleSellButton(AdvancedButton button) {
        button.setBackgroundColor(0.12f, 0.40f, 0.16f, 0.96f);
        button.setBorderColor(0.50f, 0.88f, 0.50f, 0.58f);
        button.setHoverBackgroundColor(0x2B7A35F5);
        button.setHoverBorderColor(0x9FE2A0DD);
        button.setHoverBorderWidth(1);
    }

    private void styleDisabledButton(AdvancedButton button) {
        button.setBackgroundColor(0.10f, 0.10f, 0.10f, 0.45f);
        button.setBorderColor(0.45f, 0.45f, 0.45f, 0.35f);
        button.setHoverBackgroundColor(0x1A1A1A73);
        button.setHoverBorderColor(0x73737359);
        button.setHoverBorderWidth(1);
    }

    private void styleResetButton(AdvancedButton button) {
        button.setBackgroundColor(0.46f, 0.18f, 0.12f, 0.96f);
        button.setHoverBackgroundColor(0x8A3024F5);
        button.setBorderColor(0.95f, 0.42f, 0.32f, 0.58f);
        button.setHoverBorderColor(0xD89272DD);
        button.setHoverBorderWidth(1);
    }

    private int requiredSystemSellAmount(ShopOffer offer, String amountText) {
        int quantity = quantityForAmount(offer, amountText);
        return quantity <= 0 ? 0 : Math.max(1, offer.getAmount()) * quantity;
    }

    private int quantityForAmount(ShopOffer offer, String amountText) {
        int amount = parseStrictPositiveInt(amountText);
        return amount <= 0 ? 0 : Math.max(1, (int) Math.ceil((double) amount / Math.max(1, offer.getAmount())));
    }

    private boolean canBuySelectedSystemAmount(ShopOffer offer, String amountText) {
        int quantity = quantityForAmount(offer, amountText);
        if (quantity <= 0) {
            return false;
        }
        ShopEconomyStore.EconomyState state = plugin.economyStateFor(player, offer);
        long requiredAmount = (long) Math.max(1, offer.getAmount()) * quantity;
        if (offer.getStockMode() != ShopStockMode.STATIC && state != null && state.limited()
                && state.stock() < requiredAmount) {
            return false;
        }
        String disabledReason = plugin.systemBuyDisabledReason(player, offer, quantity);
        if (disabledReason != null && !disabledReason.isBlank()) {
            return false;
        }
        long price;
        try {
            price = plugin.dynamicEconomyOffer(player, offer, quantity).getPrice(player);
        } catch (RuntimeException ex) {
            return false;
        }
        if (price <= 0L) {
            return true;
        }
        WalletBridge.BalanceInfo balance = walletBridge.balance(player.getDbID(), currencyIdentifier(offer));
        return balance.success() && balance.balance() >= price;
    }

    private String stockModeLabel(ShopStockMode stockMode) {
        ShopStockMode mode = stockMode == null ? ShopStockMode.STATIC : stockMode;
        return t.get("TC_SHOP_UI_STOCK_MODE_" + mode.name(), player);
    }

    private boolean matchesSystemOfferFilter(ShopOffer offer) {
        String filter = systemOfferFilter == null ? "" : systemOfferFilter.trim().toLowerCase(Locale.ROOT);
        if (filter.isBlank()) {
            return true;
        }
        ShopOffer displayOffer = plugin.configuredSystemOffer(player, offer);
        return offerTitle(displayOffer).toLowerCase(Locale.ROOT).contains(filter)
                || displayOffer.getItemName().toLowerCase(Locale.ROOT).contains(filter)
                || displayOffer.getTitle(player).toLowerCase(Locale.ROOT).contains(filter);
    }

    private void applySystemCardStyle(OZUIElement card, boolean selected) {
        card.setBackgroundColor(selected ? 0.18f : 0.10f, selected ? 0.15f : 0.09f,
                selected ? 0.10f : 0.08f, 0.94f);
        card.setBorder(selected ? 2 : 1);
        card.setBorderColor(0.95f, 0.75f, 0.25f, selected ? 0.9f : 0.42f);
    }

    private String sellLimitLabel(ShopOffer offer) {
        if (offer == null || !offer.usesPlayerSellLimits()) {
            return t.get("TC_SHOP_UI_LIMIT_NONE", player);
        }
        long playerLimit = offer.getPerPlayerDailySellLimit();
        long globalLimit = offer.getGlobalDailySellLimit();
        if (playerLimit <= 0L && globalLimit <= 0L) {
            return t.get("TC_SHOP_UI_LIMIT_NONE", player);
        }
        String playerPart = playerLimit > 0L
                ? t.get("TC_SHOP_UI_LIMIT_PLAYER", player).replace("PH_LIMIT", String.valueOf(playerLimit))
                : "";
        String globalPart = globalLimit > 0L
                ? t.get("TC_SHOP_UI_LIMIT_GLOBAL", player).replace("PH_LIMIT", String.valueOf(globalLimit))
                : "";
        if (playerPart.isBlank()) {
            return globalPart;
        }
        if (globalPart.isBlank()) {
            return playerPart;
        }
        return playerPart + ", " + globalPart;
    }

    private String economyDisabledLabel(String buyDisabled, String sellDisabled) {
        boolean buyBlocked = buyDisabled != null && !buyDisabled.isBlank();
        boolean sellBlocked = sellDisabled != null && !sellDisabled.isBlank();
        if (!buyBlocked && !sellBlocked) {
            return "";
        }
        if (buyBlocked && sellBlocked && buyDisabled.equals(sellDisabled)) {
            return t.get("TC_SHOP_UI_ECONOMY_BLOCKED", player).replace("PH_REASON", buyDisabled);
        }
        if (buyBlocked && sellBlocked) {
            return t.get("TC_SHOP_UI_ECONOMY_BLOCKED_BUY_SELL", player)
                    .replace("PH_BUY_REASON", buyDisabled)
                    .replace("PH_SELL_REASON", sellDisabled);
        }
        if (buyBlocked) {
            return t.get("TC_SHOP_UI_ECONOMY_BLOCKED_BUY", player).replace("PH_REASON", buyDisabled);
        }
        return t.get("TC_SHOP_UI_ECONOMY_BLOCKED_SELL", player).replace("PH_REASON", sellDisabled);
    }

    private UITextField textField(String value) {
        UITextField field = new UITextField(value == null ? "" : value);
        field.setReadOnly(false);
        field.setBackgroundColor(0.02f, 0.02f, 0.02f, 0.78f);
        field.setBorder(1);
        field.setBorderColor(0.95f, 0.75f, 0.25f, 0.46f);
        field.setBorderEdgeRadius(4, false);
        field.setFontSize(13);
        return field;
    }

    private static int parseStrictPositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
        return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void setupZoneTab() {
        Area area = player.getCurrentArea();
        if (area == null || area.getID() <= 0L) {
            UILabel empty = label(t.get("TC_SHOP_ZONE_NO_AREA", player), 15, Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(18, 18, false);
            empty.setSize(90, 40, true);
            empty.setTextWrap(true);
            body.addChild(empty);
            return;
        }
        ShopZone zone = plugin.currentShopZone(player).orElse(null);
        if (zone == null) {
            setupUnmarkedZone(area);
            return;
        }
        setupMarkedZone(area, zone);
    }

    private void setupUnmarkedZone(Area area) {
        UILabel title = label(t.get("TC_SHOP_UI_ZONE_CURRENT_TITLE", player), 20, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 18, false);
        title.setSize(520, 30, false);
        body.addChild(title);

        addZoneLine(18, 64, t.get("TC_SHOP_UI_ZONE_AREA_NAME", player)
                .replace("PH_AREA", areaName(area)));
        addZoneLine(18, 88, t.get("TC_SHOP_UI_ZONE_AREA_ID", player)
                .replace("PH_AREA_ID", String.valueOf(area.getID())));

        AdvancedButton create = AdvancedButtonFactory.defaultButton(t.get("TC_MENU_SHOP_ZONE_CREATE", player), event -> {
            plugin.createOrEnableCurrentZone(player).ifPresentOrElse(zone -> {
                player.sendTextMessage(c.okay + t.get("TC_SHOP_ZONE_CREATED", player)
                        .replace("PH_AREA", zone.getAreaName())
                        .replace("PH_AREA_ID", String.valueOf(zone.getAreaId())));
                activeTab = Tab.ZONE;
                rebuild();
            }, () -> player.sendTextMessage(c.warning + t.get("TC_SHOP_ZONE_NO_AREA", player)));
        });
        create.setPivot(Pivot.UpperLeft);
        create.setPosition(18, 130, false);
        create.setSize(220, 32, false);
        body.addChild(create);
    }

    private void setupMarkedZone(Area area, ShopZone zone) {
        UILabel title = label(t.get("TC_SHOP_UI_ZONE_CONFIG_TITLE", player), 20, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 18, false);
        title.setSize(520, 30, false);
        body.addChild(title);

        addZoneLine(18, 54, t.get("TC_SHOP_UI_ZONE_AREA_INFO", player)
                .replace("PH_AREA", areaName(area))
                .replace("PH_AREA_ID", String.valueOf(area.getID())));

        addZoneLabel(18, 92, t.get("TC_SHOP_UI_ZONE_NAME_LABEL", player));
        zoneNameDraft = zone.getAreaName();
        zoneNameField = textField(zone.getAreaName());
        zoneNameField.setPivot(Pivot.UpperLeft);
        zoneNameField.setPosition(18, 118, false);
        zoneNameField.setSize(320, 30, false);
        zoneNameField.setMaxCharacters(80);
        body.addChild(zoneNameField);
        body.addChild(zoneButton(t.get("TC_SHOP_UI_SAVE", player), 354, 118, 118,
                event -> zoneNameField.getCurrentText(player, value -> {
                    ShopZone updated = plugin.setZoneName(zone.getAreaId(), value);
                    if (updated == null) {
                        player.sendTextMessage(c.error + t.get("TC_SHOP_UI_ZONE_NAME_UPDATE_FAILED", player));
                    } else {
                        player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_ZONE_NAME_UPDATED", player)
                                .replace("PH_AREA", updated.getAreaName()));
                    }
                    rebuild();
                })));
        body.addChild(zoneButton(t.get("TC_SHOP_UI_ZONE_SYNC_NAME", player), 486, 118, 150,
                event -> {
                    ShopZone updated = plugin.syncCurrentZoneName(player);
                    if (updated == null) {
                        player.sendTextMessage(c.error + t.get("TC_SHOP_UI_ZONE_NAME_UPDATE_FAILED", player));
                    } else {
                        player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_ZONE_NAME_UPDATED", player)
                                .replace("PH_AREA", updated.getAreaName()));
                    }
                    rebuild();
                }));

        addZoneLabel(18, 172, t.get("TC_SHOP_UI_ZONE_OFFERS_FILE_LABEL", player));
        zoneOfferFileDraft = zone.getSystemOffersFile();
        zoneOfferFileField = textField(zone.getSystemOffersFile());
        zoneOfferFileField.setPivot(Pivot.UpperLeft);
        zoneOfferFileField.setPosition(18, 198, false);
        zoneOfferFileField.setSize(320, 30, false);
        zoneOfferFileField.setMaxCharacters(120);
        body.addChild(zoneOfferFileField);
        body.addChild(zoneButton(t.get("TC_SHOP_UI_SAVE", player), 354, 198, 118,
                event -> zoneOfferFileField.getCurrentText(player, value -> {
                    updateZoneOfferFile(zone, value);
                    rebuild();
                })));
        body.addChild(zoneButton(t.get("TC_SHOP_UI_ZONE_OFFERS_RESET", player), 486, 198, 150,
                event -> {
                    updateZoneOfferFile(zone, "");
                    rebuild();
                }));

        addZoneLabel(18, 254, t.get("TC_SHOP_UI_ZONE_SYSTEMSHOP_LABEL", player));
        body.addChild(systemShopModeButton(zone, -1, 18, 284));
        body.addChild(systemShopModeButton(zone, 0, 140, 284));
        body.addChild(systemShopModeButton(zone, 1, 262, 284));

        body.addChild(zoneButton(t.get("TC_SHOP_UI_ZONE_RESET_STOCKS", player), 18, 354, 260,
                event -> {
                    pendingResetZoneStocks = true;
                    rebuild();
                }));
        UIElement remove = zoneButton(t.get("TC_SHOP_UI_ZONE_REMOVE", player), 304, 354, 220,
                event -> {
                    pendingRemoveZone = zone;
                    rebuild();
                });
        if (remove instanceof AdvancedButton removeButton) {
            styleResetButton(removeButton);
        }
        body.addChild(remove);
    }

    private void addZoneLabel(int x, int y, String text) {
        UILabel label = label(text, 13, Font.DefaultBold);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(x, y, false);
        label.setSize(320, 22, false);
        label.setFontColor(0xF2C766FF);
        body.addChild(label);
    }

    private void addZoneLine(int x, int y, String text) {
        UILabel label = label(text, 14, Font.Default);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(x, y, false);
        label.setSize(560, 22, false);
        label.setFontColor(0xC8C0B2FF);
        body.addChild(label);
    }

    private UIElement zoneButton(String text, int x, int y, int width, java.util.function.Consumer<Object> action) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text, event -> action.accept(event));
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(width, 30, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private UIElement systemShopModeButton(ShopZone zone, int mode, int x, int y) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(systemShopModeLabel(mode), event -> {
            ShopZone updated = plugin.setZoneSystemShop(zone.getAreaId(), mode);
            if (updated != null) {
                player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_SYSTEMSHOP_UPDATED", player)
                        .replace("PH_AREA", updated.getAreaName())
                        .replace("PH_MODE", systemShopLabel(updated)));
            }
            rebuild();
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(108, 30, false);
        button.setBorder(1);
        button.setBorderEdgeRadius(3, false);
        if (zone.getSystemShop() == mode) {
            button.setBackgroundColor(0.54f, 0.38f, 0.07f, 0.96f);
            button.setBorderColor(0.95f, 0.75f, 0.25f, 0.72f);
        }
        return button;
    }

    private String systemShopModeLabel(int mode) {
        return switch (mode) {
            case 0 -> t.get("TC_SHOP_UI_SYSTEMSHOP_DISABLED", player);
            case 1 -> t.get("TC_SHOP_UI_SYSTEMSHOP_ENABLED", player);
            default -> t.get("TC_SHOP_UI_SYSTEMSHOP_INHERIT", player);
        };
    }

    private void updateZoneOfferFile(ShopZone zone, String value) {
        ShopZone updated = plugin.setZoneSystemOffersFile(zone.getAreaId(), value == null ? "" : value.trim());
        if (updated == null) {
            player.sendTextMessage(c.error + t.get("TC_SHOP_UI_ZONE_OFFERS_UPDATE_FAILED", player));
            return;
        }
        plugin.reloadShopZones();
        player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_ZONE_OFFERS_UPDATED", player)
                .replace("PH_AREA", updated.getAreaName())
                .replace("PH_FILE", updated.getSystemOffersFile().isBlank()
                        ? t.get("TC_SHOP_UI_ZONE_OFFERS_DEFAULT", player)
                        : updated.getSystemOffersFile()));
    }

    private static String areaName(Area area) {
        return area.getName() == null || area.getName().isBlank() ? "Area #" + area.getID() : area.getName();
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
        AdvancedButton button = AdvancedButtonFactory.defaultButton(systemShopLabel(zone), event -> {
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
        AdvancedButton button = AdvancedButtonFactory.defaultButton(t.get("TC_SHOP_UI_REMOVE", player), event -> {
            pendingRemoveZone = zone;
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

    private void setupRemoveConfirmation() {
        if (pendingRemoveZone == null) {
            return;
        }

        OZUIElement blocker = new OZUIElement();
        blocker.setPivot(Pivot.UpperLeft);
        blocker.setPosition(0, 0, true);
        blocker.setSize(100, 100, true);
        blocker.setBackgroundColor(0, 0, 0, 0.54f);
        blocker.setClickable(true);

        OZUIElement dialog = new OZUIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(420, 190, false);
        dialog.setBackgroundColor(0.08f, 0.07f, 0.06f, 0.98f);
        dialog.setBorder(1);
        dialog.setBorderColor(0.95f, 0.75f, 0.25f, 0.74f);
        dialog.setBorderEdgeRadius(6, false);

        UILabel title = label(t.get("TC_SHOP_UI_REMOVE_CONFIRM_TITLE", player), 20, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 16, false);
        title.setSize(380, 28, false);
        dialog.addChild(title);

        UILabel text = label(t.get("TC_SHOP_UI_REMOVE_CONFIRM_TEXT", player)
                .replace("PH_AREA", pendingRemoveZone.getAreaName()), 14, Font.Default);
        text.setPivot(Pivot.UpperLeft);
        text.setPosition(18, 56, false);
        text.setSize(380, 58, false);
        text.setTextWrap(true);
        text.setTextAlign(TextAnchor.UpperLeft);
        dialog.addChild(text);

        UIElement cancel = AdvancedButtonFactory.cancel(t.get("TC_BTN_CANCEL", player), event -> {
            pendingRemoveZone = null;
            rebuild();
        });
        cancel.setPivot(Pivot.LowerLeft);
        cancel.setPosition(18, 172, false);
        cancel.setSize(150, 30, false);
        dialog.addChild(cancel);

        UIElement remove = AdvancedButtonFactory.danger(t.get("TC_SHOP_UI_REMOVE", player), event -> {
            if (plugin.shopZoneService().deleteAreaZone(pendingRemoveZone.getAreaId())) {
                plugin.reloadShopZones();
                player.sendTextMessage(c.okay + t.get("TC_SHOP_UI_AREA_REMOVED", player)
                        .replace("PH_AREA", pendingRemoveZone.getAreaName()));
            }
            pendingRemoveZone = null;
            rebuild();
        });
        remove.setPivot(Pivot.LowerRight);
        remove.setPosition(402, 172, false);
        remove.setSize(150, 30, false);
        dialog.addChild(remove);

        blocker.addChild(dialog);
        panel.addChild(blocker);
    }

    private void setupZoneStockResetConfirmation() {
        if (!pendingResetZoneStocks) {
            return;
        }

        ShopZone zone = plugin.currentShopZone(player).orElse(null);
        if (zone == null) {
            pendingResetZoneStocks = false;
            return;
        }

        OZUIElement blocker = new OZUIElement();
        blocker.setPivot(Pivot.UpperLeft);
        blocker.setPosition(0, 0, true);
        blocker.setSize(100, 100, true);
        blocker.setBackgroundColor(0, 0, 0, 0.54f);
        blocker.setClickable(true);

        OZUIElement dialog = new OZUIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(460, 200, false);
        dialog.setBackgroundColor(0.08f, 0.07f, 0.06f, 0.98f);
        dialog.setBorder(1);
        dialog.setBorderColor(0.95f, 0.75f, 0.25f, 0.74f);
        dialog.setBorderEdgeRadius(6, false);

        UILabel title = label(t.get("TC_SHOP_UI_ZONE_RESET_CONFIRM_TITLE", player), 20, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(18, 16, false);
        title.setSize(420, 28, false);
        dialog.addChild(title);

        UILabel text = label(t.get("TC_SHOP_UI_ZONE_RESET_CONFIRM_TEXT", player)
                .replace("PH_AREA", zone.getAreaName()), 14, Font.Default);
        text.setPivot(Pivot.UpperLeft);
        text.setPosition(18, 56, false);
        text.setSize(420, 64, false);
        text.setTextWrap(true);
        text.setTextAlign(TextAnchor.UpperLeft);
        dialog.addChild(text);

        UIElement cancel = AdvancedButtonFactory.cancel(t.get("TC_BTN_CANCEL", player), event -> {
            pendingResetZoneStocks = false;
            rebuild();
        });
        cancel.setPivot(Pivot.LowerLeft);
        cancel.setPosition(18, 182, false);
        cancel.setSize(150, 30, false);
        dialog.addChild(cancel);

        UIElement confirm = AdvancedButtonFactory.ok(t.get("TC_SHOP_UI_ZONE_RESET_STOCKS", player), event -> {
            pendingResetZoneStocks = false;
            ShopPurchaseResult result = plugin.resetCurrentZoneStocksToTarget(player);
            player.sendTextMessage((result.success ? c.okay : c.error) + result.message);
            rebuild();
        });
        confirm.setPivot(Pivot.LowerRight);
        confirm.setPosition(442, 182, false);
        confirm.setSize(230, 30, false);
        dialog.addChild(confirm);

        blocker.addChild(dialog);
        panel.addChild(blocker);
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

    public void close() {
        player.removeUIElement(this);
        player.deleteAttribute("oz.shop.ui.overlay");
        CursorManager.hide(player);
    }
}
