package de.omegazirkel.risingworld;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import de.omegazirkel.risingworld.shop.ShopEconomyStore;
import de.omegazirkel.risingworld.shop.DynamicEconomyPricing;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopStockMode;

public class ShopDynamicEconomyTest {
    @Test
    public void generatedHairstylesStayWithinTheSupportedGenderedRanges() {
        for (int seed = 0; seed < 100; seed++) {
            assertTrue(ShopRuntime.randomHairstyle(true, new Random(seed)) >= 50);
            assertTrue(ShopRuntime.randomHairstyle(true, new Random(seed)) <= 68);
            assertTrue(ShopRuntime.randomHairstyle(false, new Random(seed)) >= 100);
            assertTrue(ShopRuntime.randomHairstyle(false, new Random(seed)) <= 119);
        }
    }

    @Test
    public void staticPricingAppliesSpreadAndRounding() {
        Shop.DynamicEconomyPrices prices = Shop.dynamicEconomyPrices(offer(10.5d), null, 2, false);

        assertEquals(19L, prices.buyPrice());
        assertEquals(24L, prices.sellPrice());
        assertEquals(10.5d, prices.averageUnitPrice(), 0.0001d);
        assertTrue(prices.sellPrice() > prices.buyPrice());
    }

    @Test
    public void stockPressureRaisesOutboundPriceAndBulkTradesStepStock() {
        ShopOffer offer = offer(10.0d);
        ShopEconomyStore.EconomyState lowStock = new ShopEconomyStore.EconomyState(1L, 10L, 20L, 0.0d, 0.0d);

        Shop.DynamicEconomyPrices one = Shop.dynamicEconomyPrices(offer, lowStock, 1);
        Shop.DynamicEconomyPrices two = Shop.dynamicEconomyPrices(offer, lowStock, 2);

        assertEquals(45L, one.sellPrice());
        assertEquals(90L, two.sellPrice());
        assertTrue(two.buyPrice() <= one.buyPrice() * 2L);
    }

    @Test
    public void bulkBuybacksUseEachIncomingItemsStockLevel() {
        ShopOffer offer = offer(10.0d);
        ShopEconomyStore.EconomyState lowStock = new ShopEconomyStore.EconomyState(2L, 10L, 20L, 0.0d, 0.0d);

        Shop.DynamicEconomyPrices prices = Shop.dynamicEconomyPrices(offer, lowStock, 3);

        // 10 * (4 + 10/3 + 2.5), after the buyback spread and rounded once.
        assertEquals(87L, prices.buyPrice());
    }

    @Test
    public void stockPriceStaysAtBaseUntilTargetAndReachesTheConfiguredMinimumOnlyAtLimit() {
        ShopOffer offer = offer(6.0d);
        ShopEconomyStore.EconomyState atTarget = new ShopEconomyStore.EconomyState(10L, 10L, 500L, 0.0d, 0.0d);
        ShopEconomyStore.EconomyState atLimit = new ShopEconomyStore.EconomyState(500L, 10L, 500L, 0.0d, 0.0d);
        ShopEconomyStore.EconomyState atForty = new ShopEconomyStore.EconomyState(40L, 10L, 500L, 0.0d, 0.0d);

        assertEquals(6.0d, Shop.dynamicEconomyPrices(offer, atTarget, 1).averageUnitPrice(), 0.0001d);
        assertEquals(1.5d, Shop.dynamicEconomyPrices(offer, atLimit, 1).averageUnitPrice(), 0.0001d);
        assertTrue(Shop.dynamicEconomyPrices(offer, atForty, 1).averageUnitPrice() > 5.0d);
        assertEquals(3L, Shop.dynamicEconomyPrices(offer, atLimit, 2).buyPrice());
    }

    @Test
    public void traderStockSettlementUsesTheSameSteppedOutboundPriceAsPlayerPurchases() {
        ShopOffer offer = offer(10.0d);
        ShopEconomyStore.EconomyState stock = new ShopEconomyStore.EconomyState(2L, 10L, 20L, 0.0d, 0.0d);

        assertEquals(90L, DynamicEconomyPricing.outboundValue(offer, stock, 2L, true));
        assertEquals(23L, DynamicEconomyPricing.outboundValue(offer, stock, 2L, false));
    }

    private static ShopOffer offer(double basePrice) {
        return new ShopOffer("test", "Test", "", "stone", (short) 0, 0, 1, basePrice, 0L, 0L,
                "COINS", "", "", "system", "system", true, false, true, 1L, 10L, 20L, 0.0d, 0.0d,
                ShopStockMode.HYBRID, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 10L, 20L, null, null);
    }
}
