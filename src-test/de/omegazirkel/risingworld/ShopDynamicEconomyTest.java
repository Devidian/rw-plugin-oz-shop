package de.omegazirkel.risingworld;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.omegazirkel.risingworld.shop.ShopEconomyStore;
import de.omegazirkel.risingworld.shop.ShopOffer;
import de.omegazirkel.risingworld.shop.ShopStockMode;

public class ShopDynamicEconomyTest {
    @Test
    public void staticPricingAppliesSpreadAndRounding() {
        Shop.DynamicEconomyPrices prices = Shop.dynamicEconomyPrices(offer(10.5d), null, 2, false);

        assertEquals(18L, prices.buyPrice());
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
        assertTrue(two.buyPrice() < one.buyPrice() * 2L);
    }

    private static ShopOffer offer(double basePrice) {
        return new ShopOffer("test", "Test", "", "stone", (short) 0, 0, 1, basePrice, 0L, 0L,
                "COINS", "", "", "system", "system", true, false, true, 1L, 10L, 20L, 0.0d, 0.0d,
                ShopStockMode.HYBRID, 0.25d, 4.0d, 25.0d, 10.0d, 5L, 10.0d, 5L, 10L, 20L, null, null);
    }
}
