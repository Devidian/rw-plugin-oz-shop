package de.omegazirkel.risingworld.shop.exports;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopEconomyStore;
import de.omegazirkel.risingworld.shop.Trader;
import de.omegazirkel.risingworld.shop.WalletBridge;
import de.omegazirkel.risingworld.shop.ShopItemNames;
import net.risingworld.api.World;
import net.risingworld.api.objects.Npc;

public final class ShopTraderExportService {
    private final Supplier<List<Trader>> traders;
    private final Function<Trader, List<ShopOffer>> offers;
    private final java.util.function.BiFunction<Trader, ShopOffer, ShopOffer> currentOffers;
    private final java.util.function.BiFunction<Trader, ShopOffer, ShopEconomyStore.EconomyState> economyStates;
    private final Function<String, List<WalletBridge.SystemBalanceInfo>> balances;
    public ShopTraderExportService(Supplier<List<Trader>> traders, Function<Trader, List<ShopOffer>> offers,
            java.util.function.BiFunction<Trader, ShopOffer, ShopOffer> currentOffers,
            java.util.function.BiFunction<Trader, ShopOffer, ShopEconomyStore.EconomyState> economyStates,
            Function<String, List<WalletBridge.SystemBalanceInfo>> balances) {
        this.traders = traders;
        this.offers = offers;
        this.currentOffers = currentOffers;
        this.economyStates = economyStates;
        this.balances = balances;
    }
    public ShopTradersExportResponse exportTraders() {
        return new ShopTradersExportResponse(1, traders.get().stream().flatMap(trader -> {
            Npc npc = World.getNpc(trader.npcId()); if (npc == null) return java.util.stream.Stream.empty();
            List<ShopBalanceExport> accountBalances = balances.apply(trader.accountId()).stream()
                    .map(balance -> new ShopBalanceExport(balance.currencyIdentifier(), balance.balance())).toList();
            List<ShopOfferExport> traderOffers = offers.apply(trader).stream().filter(ShopOffer::isEnabled)
                    .map(offer -> exportOffer(trader, offer)).toList();
            return java.util.stream.Stream.of(new ShopTraderExport(trader.npcId(), trader.name(), npc.getPosition().x,
                    npc.getPosition().y, npc.getPosition().z, accountBalances, traderOffers));
        }).toList());
    }

    private ShopOfferExport exportOffer(Trader trader, ShopOffer offer) {
        ShopOffer currentOffer = currentOffers.apply(trader, offer);
        ShopEconomyStore.EconomyState state = economyStates.apply(trader, offer);
        long stock = state == null ? offer.getDefaultStock() : state.stock();
        return new ShopOfferExport(currentOffer.getId(), currentOffer.getItemName(), currentOffer.getItemVariant(),
                currentOffer.getAmount(), currentOffer.getPrice(), stock, currentOffer.getCurrencyIdentifier(),
                currentOffer.getSellPrice(), currentOffer.getBuyPrice(), currentOffer.getDefaultStockLimit(),
                ShopItemNames.label(currentOffer.getItemName(), currentOffer.getItemVariant(), currentOffer.getTitle(null), "de"),
                ShopItemNames.label(currentOffer.getItemName(), currentOffer.getItemVariant(), currentOffer.getTitle(null), "en"));
    }
}
