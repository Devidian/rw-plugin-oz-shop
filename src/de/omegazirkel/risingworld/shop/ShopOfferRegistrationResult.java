package de.omegazirkel.risingworld.shop;

public class ShopOfferRegistrationResult {
    public final boolean success;
    public final ShopErrorCode errorCode;
    public final String message;
    public final ShopOffer offer;

    private ShopOfferRegistrationResult(boolean success, ShopErrorCode errorCode, String message, ShopOffer offer) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.offer = offer;
    }

    public static ShopOfferRegistrationResult success(String message, ShopOffer offer) {
        return new ShopOfferRegistrationResult(true, ShopErrorCode.NONE, message, offer);
    }

    public static ShopOfferRegistrationResult failure(ShopErrorCode errorCode, String message) {
        return new ShopOfferRegistrationResult(false, errorCode, message, null);
    }
}
