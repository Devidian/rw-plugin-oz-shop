package de.omegazirkel.risingworld.shop.exports;

import java.util.List;

public record ShopTraderExport(long npcId, String name, float x, float y, float z,
        List<ShopBalanceExport> balances, List<ShopOfferExport> offers) { }
