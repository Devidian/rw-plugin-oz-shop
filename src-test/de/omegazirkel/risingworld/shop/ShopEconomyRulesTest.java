package de.omegazirkel.risingworld.shop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.risingworld.api.definitions.Items.Modifier;

public class ShopEconomyRulesTest {
    @Test
    public void durabilityAdjustedPayoutIsLinearPerItemAndFloorsWholeCurrency() {
        assertEquals(100L, ShopService.durabilityAdjustedPayout(100L, 100, 100));
        assertEquals(10L, ShopService.durabilityAdjustedPayout(100L, 10, 100));
        assertEquals(3L, ShopService.durabilityAdjustedPayout(35L, 10, 100));
        assertEquals(0L, ShopService.durabilityAdjustedPayout(100L, 0, 100));
        assertEquals(100L, ShopService.durabilityAdjustedPayout(100L, 0, 0));
    }

    @Test
    public void stackPayoutRetainsFractionalUnitValueUntilTheWholeStackIsTotaled() {
        double unitPayout = 25L / 100.0d;

        assertEquals(25L, (long) Math.floor(
                ShopService.conditionAdjustedPayoutExact(unitPayout, 0, 0, Modifier.Normal) * 100));
    }

    @Test
    public void modifierAdjustedPayoutUsesDefinedBrokenNormalAndGodlyBounds() {
        assertEquals(10L, ShopService.conditionAdjustedPayout(100L, 100, 100, Modifier.Broken));
        assertEquals(100L, ShopService.conditionAdjustedPayout(100L, 100, 100, Modifier.Normal));
        assertEquals(1_000L, ShopService.conditionAdjustedPayout(100L, 100, 100, Modifier.Godly));
        assertTrue(ShopService.modifierPayoutMultiplier(Modifier.Shoddy) < 1.0d);
        assertTrue(ShopService.modifierPayoutMultiplier(Modifier.Nice) > 1.0d);
    }

    @Test
    public void automaticTicksOnlyRunForSuppliedModes() {
        assertFalse(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.STATIC)));
        assertFalse(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.PLAYER_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.LOOT)));
        assertTrue(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.SYSTEM_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticTicksEnabled(offer(ShopStockMode.HYBRID)));
        assertFalse(ShopEconomyStore.automaticDrainEnabled(offer(ShopStockMode.SYSTEM_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticRestockEnabled(offer(ShopStockMode.SYSTEM_SUPPLIED)));
        assertTrue(ShopEconomyStore.automaticDrainEnabled(offer(ShopStockMode.HYBRID)));
        assertTrue(ShopEconomyStore.automaticRestockEnabled(offer(ShopStockMode.HYBRID)));
        assertTrue(ShopEconomyStore.automaticDrainEnabled(offer(ShopStockMode.LOOT)));
        assertFalse(ShopEconomyStore.automaticRestockEnabled(offer(ShopStockMode.LOOT)));
    }

    @Test
    public void targetTickAmountsRespectPercentCapsAndHourlyReconciliation() {
        assertEquals(50L, ShopEconomyStore.targetDrainAmount(1_000L, 10.0d, 50L, 1.0d));
        assertEquals(0L, ShopEconomyStore.targetDrainAmount(1_000L, 10.0d, 50L, 0.5d));
        assertEquals(0L, ShopEconomyStore.targetRestockAmount(1_000L, 10.0d, 0L, 0.5d));
        assertEquals(120L, ShopEconomyStore.targetRestockAmount(1_000L, 10.0d, 5L, 24.0d));
        assertEquals(1L, ShopEconomyStore.targetRestockAmount(10L, 10.0d, 1_000L, 1.0d));
        assertEquals(10L, ShopEconomyStore.targetRestockAmount(10L, 10.0d, 1_000L, 10.0d));
    }

    @Test
    public void drainUsesTheConfiguredEconomyIntervalLikeRestock() throws Exception {
        long[] now = { 1_000L };
        ShopOffer offer = offer(ShopStockMode.LOOT).economyConfigCopy(100L, 100L, 0.0d, 0.0d,
                ShopStockMode.LOOT, 0.25d, 4.0d, 25.0d, 10.0d, 0L, 0.0d, 0L, 0L, 0L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.setTickIntervalHours(2);
            store.configure("global", offer.getId(), 120L, 0.0d, 0.0d);
            setLastTick(connection, offer.getId(), now[0]);

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(offer), java.util.List.of());
            assertEquals(120L, store.stateFor("global", offer).stock());

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(offer), java.util.List.of());
            assertEquals(100L, store.stateFor("global", offer).stock());
        }
    }

    @Test
    public void tickStatusUsesTheConfiguredEconomyIntervalForDrainAndRestock() throws Exception {
        long[] now = { 1_000L };
        ShopOffer offer = offer(ShopStockMode.HYBRID);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.setTickIntervalHours(2);
            store.configure("global", offer.getId(), 50L, 0.0d, 0.0d);
            setLastTick(connection, offer.getId(), now[0]);

            ShopEconomyStore.EconomyTickStatus status = store.tickStatusFor("global", offer);

            assertEquals(7_201_000L, status.nextDrainAt());
            assertEquals(7_201_000L, status.nextRestockAt());
        }
    }

    @Test
    public void fractionalChangesAccumulateUntilTheConfiguredTickCanChangeStock() throws Exception {
        long[] now = { 1_000L };
        ShopOffer offer = offer(ShopStockMode.SYSTEM_SUPPLIED).economyConfigCopy(10L, 10L, 0.0d, 0.0d,
                ShopStockMode.SYSTEM_SUPPLIED, 0.25d, 4.0d, 25.0d, 0.0d, 0L, 5.0d, 0L, 0L, 0L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.configure("global", offer.getId(), 0L, 0.0d, 0.0d);
            setLastTick(connection, offer.getId(), now[0]);

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(offer), java.util.List.of());
            assertEquals(0L, store.stateFor("global", offer).stock());
            assertEquals(1_000L, lastTick(connection, offer.getId()));

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(offer), java.util.List.of());
            assertEquals(1L, store.stateFor("global", offer).stock());
            assertEquals(now[0], lastTick(connection, offer.getId()));
        }
    }

    @Test
    public void lootTicksDrainButNeverRestock() throws Exception {
        long[] now = { 1_000L };
        ShopOffer offer = offer(ShopStockMode.LOOT).economyConfigCopy(10L, 10L, 0.0d, 0.0d,
                ShopStockMode.LOOT, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 0L, 0L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.configure("global", offer.getId(), 0L, 0.0d, 0.0d);
            setLastTick(connection, offer.getId(), now[0]);

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(offer), java.util.List.of());

            assertEquals(0L, store.stateFor("global", offer).stock());
        }
    }

    @Test
    public void zoneAndGlobalTicksKeepStockOnTheCorrectSideOfTarget() throws Exception {
        long[] now = { 1_000L };
        ShopOffer system = offer(ShopStockMode.SYSTEM_SUPPLIED);
        ShopOffer hybrid = offer(ShopStockMode.HYBRID);
        ShopOffer loot = offer(ShopStockMode.LOOT);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.configure("global", system.getId(), 5L, 0.0d, 0.0d);
            store.configure("area:42", hybrid.getId(), 15L, 0.0d, 0.0d);
            store.configure("area:42", loot.getId(), 5L, 0.0d, 0.0d);
            setLastTick(connection, system.getId(), now[0]);
            setLastTick(connection, "area:42", hybrid.getId(), now[0]);
            setLastTick(connection, "area:42", loot.getId(), now[0]);
            assertEquals(15L, store.stateForWithoutTick("area:42", hybrid).stock());

            now[0] += 3_600_000L;
            store.applyTicks(java.util.List.of(system, hybrid, loot),
                    java.util.List.of(new ShopZone(42L, "Test", "admin", now[0])));

            assertEquals(6L, store.stateFor("global", system).stock());
            assertEquals(14L, store.stateFor("area:42", hybrid).stock());
            assertEquals(5L, store.stateFor("area:42", loot).stock());
        }
    }

    @Test
    public void scopeReconciliationCommitsAllChangedOffersWithOneScopeTimestamp() throws Exception {
        long[] now = { 1_000L };
        ShopOffer first = offer(ShopStockMode.SYSTEM_SUPPLIED);
        ShopOffer second = new ShopOffer("second", "Second", "", "stone", (short) 0, 0, 1, 10.0d, 0L, 0L,
                "COINS", "", "", "system", "system", true, false, true, 1L, 10L, 20L, 0.0d, 0.0d,
                ShopStockMode.SYSTEM_SUPPLIED, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 10L, 20L, null, null);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.configure("global", first.getId(), 5L, 0.0d, 0.0d);
            store.configure("global", second.getId(), 5L, 0.0d, 0.0d);
            setLastTick(connection, first.getId(), now[0]); setLastTick(connection, second.getId(), now[0]);
            now[0] += 3_600_000L;
            ShopEconomyStore.ScopeTickResult result = store.reconcile(java.util.List.of(first, second), java.util.List.of()).get(0);
            assertEquals(2, result.changes().size());
            assertEquals(6L, store.stateForWithoutTick("global", first).stock());
            assertEquals(6L, store.stateForWithoutTick("global", second).stock());
            try (PreparedStatement statement = connection.prepareStatement("SELECT last_tick_at FROM shop_economy_scope_ticks WHERE scope = 'global'")) {
                try (java.sql.ResultSet rows = statement.executeQuery()) { assertTrue(rows.next()); assertEquals(now[0], rows.getLong(1)); }
            }
        }
    }

    @Test
    public void immediateScopeTickMakesTheSharedCountdownDue() throws Exception {
        long[] now = { 7_200_000L };
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection, () -> now[0]);
            store.setTickIntervalHours(1);
            store.initializeScope("trader:joe");

            store.requestImmediateScopeTick("trader:joe");

            assertTrue(store.scopeTickDue("trader:joe"));
            assertEquals(now[0], store.nextScopeTickAt("trader:joe"));
        }
    }

    private static void setLastTick(Connection connection, String offerId, long tick) throws Exception {
        setLastTick(connection, "global", offerId, tick);
    }

    private static void setLastTick(Connection connection, String scope, String offerId, long tick) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE shop_offer_economy_state SET last_tick_at = ? WHERE scope = ? AND offer_id = ?")) {
            statement.setLong(1, tick);
            statement.setString(2, scope);
            statement.setString(3, offerId);
            statement.executeUpdate();
        }
    }

    private static long lastTick(Connection connection, String offerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_tick_at FROM shop_offer_economy_state WHERE scope = 'global' AND offer_id = ?")) {
            statement.setString(1, offerId);
            try (java.sql.ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
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
        assertTrue(offer(ShopStockMode.LOOT).canPlayerSellToSystem());
        assertTrue(offer(ShopStockMode.HYBRID).canPlayerSellToSystem());
    }

    @Test
    public void hybridTraderDrainOnlyUsesStockAboveTarget() {
        ShopOffer hybrid = offer(ShopStockMode.HYBRID);

        assertEquals(0L, TraderService.automaticDrainBase(hybrid, 10L, 10L));
        assertEquals(1L, TraderService.automaticDrainBase(hybrid, 11L, 10L));
        assertEquals(0L, TraderService.automaticDrainBase(offer(ShopStockMode.SYSTEM_SUPPLIED), 10L, 10L));
        assertEquals(5L, TraderService.automaticDrainBase(offer(ShopStockMode.LOOT), 15L, 10L));
    }

    @Test
    public void traderBuybackRequiresTheNormalModifierPayoutBeforeWorldPremiumFunding() {
        assertFalse(TraderService.hasSufficientBasePayoutBalance(19L, 20L));
        assertTrue(TraderService.hasSufficientBasePayoutBalance(20L, 20L));
        assertTrue(TraderService.hasSufficientBasePayoutBalance(100L, 20L));
    }

    @Test
    public void traderNormalModifierCapUsesAggregatePayoutRounding() {
        assertEquals(290L, ShopService.traderPayoutCap(290L, 96.75d * 3.0d));
        assertEquals(290L, ShopService.traderPayoutCap(350L, 290.25d));
    }

    @Test
    public void quantityAwareStockCapRejectsFullRequestedAmount() throws Exception {
        ShopOffer offer = offer(ShopStockMode.HYBRID).economyCopy(3, 10.0d, 25L, 35L);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ShopEconomyStore store = new ShopEconomyStore(connection);
            assertTrue(store.configure("global", offer.getId(), 18L, 0.0d, 0.0d));

            ShopEconomyStore.EconomyCheck check = store.canBuyFromPlayer("global", 7L, offer);

            assertFalse(check.allowed());
        assertEquals("tc.shop.dynamic.stock.full", check.messageKey());
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
        assertEquals("tc.shop.daily.sell.limit.player", check.messageKey());
        }
    }

    private static ShopOffer offer(ShopStockMode mode) {
        return new ShopOffer("test-" + mode.name().toLowerCase(), "Test", "", "stone", (short) 0, 0, 1, 10.0d, 0L, 0L,
                "COINS", "", "", "system", "system", true, false, true, 1L, 10L, 20L, 0.0d, 0.0d,
                mode, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 10L, 20L, null, null);
    }
}
