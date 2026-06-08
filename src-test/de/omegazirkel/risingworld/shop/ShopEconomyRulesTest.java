package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ShopEconomyRulesTest {
    @Test
    public void automaticTicksOnlyRunForSuppliedModes() {
        assertFalse(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.STATIC)));
        assertFalse(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.PLAYER_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.SYSTEM_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.HYBRID)));
    }

    @Test
    public void targetTickAmountsRespectPercentCapsAndMinimumHour() {
        assertEquals(50L, ShopEconomyStore.targetRateAmount(1_000L, 10.0d, 50L, 1.0d));
        assertEquals(25L, ShopEconomyStore.targetRateAmount(1_000L, 10.0d, 50L, 0.5d));
        assertEquals(0L, ShopEconomyStore.targetRestockAmount(1_000L, 10.0d, 0L, 0.5d));
        assertEquals(5L, ShopEconomyStore.targetRestockAmount(1_000L, 10.0d, 5L, 24.0d));
    }

    @Test
    public void legacyFlagsRemainCompatibleButNewFlagWins() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("buyEnabled", true);
        assertTrue(SystemOfferFile.enabledValue(legacy));

        legacy.put("isEnabled", false);
        assertFalse(SystemOfferFile.enabledValue(legacy));
    }

    @Test
    public void stockModesDefinePlayerSellCapability() {
        assertFalse(offer(ShopStockMode.STATIC).canPlayerSellToSystem());
        assertFalse(offer(ShopStockMode.SYSTEM_SUPPLIED).canPlayerSellToSystem());
        assertTrue(offer(ShopStockMode.PLAYER_SUPPLIED).canPlayerSellToSystem());
        assertTrue(offer(ShopStockMode.HYBRID).canPlayerSellToSystem());
    }

    @Test
    public void quantityAwareStockCapRejectsFullRequestedAmount() throws Exception {
        ShopOffer offer = offer(ShopStockMode.HYBRID).economyCopy(3, 10.0d, 25L, 35L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection);
            assertTrue(store.configure("global", offer.getId(), 18L, 0.0d, 0.0d));

            ShopEconomyStore.EconomyCheck check = store.canBuyFromPlayer("global", 7L, offer);

            assertFalse(check.allowed());
            assertEquals("TC_SHOP_DYNAMIC_STOCK_FULL", check.messageKey());
        }
    }

    @Test
    public void persistedDailySellCountersRejectFurtherPlayerSales() throws Exception {
        ShopOffer offer = offer(ShopStockMode.HYBRID).economyCopy(6, 10.0d, 50L, 70L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection);
            store.recordSystemBuy("global", 7L, offer, 50L);

            ShopEconomyStore.EconomyCheck check = store.canBuyFromPlayer("global", 7L, offer);

            assertFalse(check.allowed());
            assertEquals("TC_SHOP_DAILY_SELL_LIMIT_PLAYER", check.messageKey());
        }
    }

    private static ShopOffer offer(ShopStockMode mode) {
        return new ShopOffer("test-" + mode.name().toLowerCase(), "Test", "", "stone", (short) 0, 0, 1, 10.0d, 0L, 0L,
                "COINS", "", "", "system", "system", true, false, true, 1L, 10L, 20L, 0.0d, 0.0d,
                mode, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 10L, 20L, null, null);
    }
}
