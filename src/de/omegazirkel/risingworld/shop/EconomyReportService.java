package de.omegazirkel.risingworld.shop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.omegazirkel.risingworld.Shop;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper;
import de.omegazirkel.risingworld.tools.bridge.MailBridge;
import net.risingworld.api.Server;
import net.risingworld.api.World;
import net.risingworld.api.objects.Npc;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector3f;

/** Optional, localized reports for committed scope stock and Wallet movements. */
public final class EconomyReportService {
    private EconomyReportService() { }

    public static void report(Shop owner, I18n translations, PluginSettings settings,
            ShopEconomyStore.ScopeTickResult result, List<ShopOffer> offers, Trader trader,
            boolean dynamicEconomyEnabled) {
        if (owner == null || translations == null || settings == null || result == null || !result.changed()
                || !enabled(settings, result.scope()) || settings.economyReportRecipients.isBlank()) return;
        Map<String, ShopOffer> offersById = new LinkedHashMap<>();
        for (ShopOffer offer : offers == null ? List.<ShopOffer>of() : offers) if (offer != null) offersById.put(offer.getId(), offer);
        MailBridge bridge = new MailBridge(owner);
        WalletBridge wallet = new WalletBridge(owner);
        for (Recipient configured : recipients(settings.economyReportRecipients).values()) {
            PlayerDatabaseHelper.findPlayerByExactName(owner, configured.name()).ifPresent(recipient -> {
                Player online = Server.getPlayerByDbID(recipient.dbId);
                String language = configured.language().isBlank() ? null : configured.language();
                ReportText report = text(translations, settings, result, offersById, wallet, trader, dynamicEconomyEnabled,
                        online, language);
                MailBridge.BridgeResult delivered = bridge.sendSystemReport(new MailBridge.PluginMailRequest(
                        owner.getDescription("name"), recipient.dbId, recipient.name, report.subject(), report.body(),
                        "economy:" + result.scope() + ':' + result.completedAt() + ':' + recipient.dbId));
                if (!delivered.success() && !"MAIL_UNAVAILABLE".equals(delivered.code()))
                    Shop.logger().warn("Could not deliver economy report to " + recipient.name + ": " + delivered.code());
            });
        }
    }

    private static ReportText text(I18n t, PluginSettings settings, ShopEconomyStore.ScopeTickResult result,
            Map<String, ShopOffer> offers, WalletBridge wallet, Trader trader, boolean dynamic, Player online, String language) {
        Map<String, Long> netByCurrency = new LinkedHashMap<>();
        StringBuilder changes = new StringBuilder();
        if (trader != null) {
            changes.append(local(t, "TC_SHOP_ECONOMY_REPORT_TRADER", online, language)
                    .replace("PH_TRADER", trader.name()).replace("PH_POSITION", traderPosition(trader)));
        }
        for (ShopEconomyStore.OfferTick change : result.changes()) {
            ShopOffer offer = offers.get(change.offerId());
            if (offer == null) continue;
            long amount = Math.abs(change.stock() - change.previousStock());
            if (amount <= 0L) continue;
            // Trader offer files may omit the currency and inherit the configured
            // shop currency. The Wallet entry is stored under that effective
            // currency, never under an empty identifier.
            String currency = effectiveCurrency(offer, settings, wallet);
            boolean restock = change.stock() > change.previousStock();
            long value = restock ? value(amount, offer.getBasePrice()) : DynamicEconomyPricing.outboundValue(offer,
                    new ShopEconomyStore.EconomyState(change.previousStock(), offer.getDefaultTargetStock(),
                            offer.getDefaultStockLimit(), offer.getDefaultDrainRate(), offer.getDefaultRefillRate()), amount, dynamic);
            netByCurrency.merge(currency, restock ? -value : value, Long::sum);
            if (changes.length() > 0) changes.append('\n');
            changes.append(local(t, restock ? "TC_SHOP_ECONOMY_REPORT_RESTOCK" : "TC_SHOP_ECONOMY_REPORT_DRAIN", online, language)
                    .replace("PH_ITEM", offer.getTitle()).replace("PH_AMOUNT", String.valueOf(amount))
                    .replace("PH_VALUE", String.valueOf(value)).replace("PH_CURRENCY", currency));
        }
        String accountId = trader == null ? wallet.worldSystemAccountId() : trader.accountId();
        for (Map.Entry<String, Long> entry : netByCurrency.entrySet()) {
            long net = entry.getValue();
            long balance = wallet.systemAccountBalances(accountId).stream()
                    .filter(item -> item.currencyIdentifier().equalsIgnoreCase(entry.getKey()))
                    .mapToLong(WalletBridge.SystemBalanceInfo::balance).findFirst().orElse(0L);
            if (changes.length() > 0) changes.append('\n');
            changes.append(local(t, "TC_SHOP_ECONOMY_REPORT_BALANCE", online, language)
                    .replace("PH_BALANCE", String.valueOf(balance)).replace("PH_CURRENCY", entry.getKey())
                    .replace("PH_RESULT", local(t, net >= 0 ? "TC_SHOP_ECONOMY_REPORT_PROFIT" : "TC_SHOP_ECONOMY_REPORT_LOSS", online, language))
                    .replace("PH_DELTA", String.valueOf(Math.abs(net))));
        }
        String subject = local(t, "TC_SHOP_ECONOMY_REPORT_SUBJECT", online, language).replace("PH_SCOPE", result.scope());
        String body = local(t, "TC_SHOP_ECONOMY_REPORT_BODY", online, language).replace("PH_SCOPE", result.scope())
                .replace("PH_CHANGES", changes);
        return new ReportText(subject, body);
    }

    private static Map<String, Recipient> recipients(String configured) {
        Map<String, Recipient> recipients = new LinkedHashMap<>();
        for (String value : configured.split(",")) {
            String[] parts = value.trim().split(";", 2);
            String name = parts[0].trim();
            if (!name.isBlank()) recipients.put(name.toLowerCase(Locale.ROOT), new Recipient(name,
                    parts.length == 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : ""));
        }
        return recipients;
    }

    private static String local(I18n t, String key, Player online, String language) {
        return language != null && !language.isBlank() ? t.get(key, language) : online == null ? t.get(key) : t.get(key, online);
    }

    private static long value(long amount, double unitValue) {
        if (amount <= 0L || unitValue <= 0d || !Double.isFinite(unitValue)) return 0L;
        double value = amount * unitValue;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(value);
    }

    private static String effectiveCurrency(ShopOffer offer, PluginSettings settings, WalletBridge wallet) {
        if (offer != null && !offer.getCurrencyIdentifier().isBlank()) return offer.getCurrencyIdentifier();
        if (settings != null && !settings.systemShopCurrency.isBlank()) return settings.systemShopCurrency;
        return wallet == null ? "" : wallet.defaultCurrencyIdentifier();
    }

    private static String traderPosition(Trader trader) {
        Npc npc = trader == null ? null : World.getNpc(trader.npcId());
        Vector3f position = npc == null ? null : npc.getPosition();
        return position == null ? "-" : String.format(Locale.ROOT, "%.0f / %.0f / %.0f", position.x, position.y, position.z);
    }

    private static boolean enabled(PluginSettings settings, String scope) {
        return "global".equals(scope) ? settings.economyReportGlobal
                : scope != null && scope.startsWith("area:") ? settings.economyReportZones
                : scope != null && scope.startsWith("trader:") && settings.economyReportTraders;
    }

    private record Recipient(String name, String language) { }
    private record ReportText(String subject, String body) { }
}
