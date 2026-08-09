package de.omegazirkel.risingworld.shop;

/** Shared, quantity-aware dynamic pricing for system offers and trader stock settlement. */
public final class DynamicEconomyPricing {
    private DynamicEconomyPricing() {
    }

    public static long outboundValue(ShopOffer offer, ShopEconomyStore.EconomyState state, long amount,
            boolean stockDependent) {
        if (offer == null || amount <= 0L) return 0L;
        return prices(offer, state, amount, stockDependent).sellPrice();
    }

    public static Prices prices(ShopOffer offer, ShopEconomyStore.EconomyState state, int amount,
            boolean stockDependent) {
        return prices(offer, state, (long) Math.max(1, amount), stockDependent);
    }

    private static Prices prices(ShopOffer offer, ShopEconomyStore.EconomyState state, long effectiveAmount,
            boolean stockDependent) {
        double basePrice = Math.max(0.0d, offer.getBasePrice());
        double spread = Math.max(1.0d, offer.getSpreadPercent()) / 100.0d;
        double buyFactor = Math.max(0.0d, 1.0d - (spread / 2.0d));
        double sellFactor = 1.0d + (spread / 2.0d);
        long startingStock = stockDependent && state != null ? Math.max(0L, state.stock()) : 0L;
        long targetStock = stockDependent && state != null ? state.targetStock() : 0L;
        long stockLimit = stockDependent && state != null ? state.stockLimit() : 0L;
        double buyPriceTotal = 0.0d;
        double sellPriceTotal = 0.0d;
        double unitPriceTotal = 0.0d;
        for (long i = 0L; i < effectiveAmount; i++) {
            long buyStock = saturatedAdd(startingStock, i);
            // A player sale raises stock one item at a time. Price every item at
            // that resulting stock level; multiplying the starting multiplier by
            // an additional stock ratio made bulk buybacks fall off too quickly.
            double buyUnitPrice = basePrice * stockPriceMultiplier(offer, buyStock, targetStock, stockLimit);
            buyPriceTotal += buyUnitPrice * buyFactor;
            unitPriceTotal += buyUnitPrice;

            long saleStock = startingStock > i ? startingStock - i : 0L;
            sellPriceTotal += basePrice * stockPriceMultiplier(offer, saleStock, targetStock, stockLimit) * sellFactor;
        }
        return new Prices(ceilPrice(buyPriceTotal), ceilPrice(sellPriceTotal), unitPriceTotal / effectiveAmount);
    }

    private static double stockPriceMultiplier(ShopOffer offer, long stock, long targetStock, long stockLimit) {
        if (targetStock <= 0L) return 1.0d;
        if (stock <= targetStock) {
            double raw = stock <= 0L ? offer.getMaxPriceMultiplier()
                    : (double) targetStock / Math.max(1.0d, stock);
            return Math.max(1.0d, Math.min(offer.getMaxPriceMultiplier(), raw));
        }
        long ceiling = Math.max(targetStock + 1L, stockLimit);
        if (ceiling <= targetStock) return 1.0d;
        double progress = Math.min(1.0d, (stock - targetStock) / (double) (ceiling - targetStock));
        double raw = 1.0d + (offer.getMinPriceMultiplier() - 1.0d) * progress;
        return Math.max(offer.getMinPriceMultiplier(), Math.min(offer.getMaxPriceMultiplier(), raw));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static long floorPrice(double value) {
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= 0.0d) return 0L;
        return (long) Math.floor(value + 1.0E-9d);
    }

    private static long ceilPrice(double value) {
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (value <= 0.0d) return 0L;
        return (long) Math.ceil(value - 1.0E-9d);
    }

    public record Prices(long buyPrice, long sellPrice, double averageUnitPrice) {
    }
}
