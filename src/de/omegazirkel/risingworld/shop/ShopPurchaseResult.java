package de.omegazirkel.risingworld.shop;

public class ShopPurchaseResult {
    public final boolean success;
    public final ShopErrorCode errorCode;
    public final String message;
    public final ShopOffer offer;

    private ShopPurchaseResult(boolean success, ShopErrorCode errorCode, String message, ShopOffer offer) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.offer = offer;
    }

    public static ShopPurchaseResult success(String message, ShopOffer offer) {
        return new ShopPurchaseResult(true, ShopErrorCode.NONE, message, offer);
    }

    public static ShopPurchaseResult failure(ShopErrorCode errorCode, String message) {
        return new ShopPurchaseResult(false, errorCode, message, null);
    }
}
