package de.omegazirkel.risingworld.shop;

/** Result of an opt-in offer policy. A non-empty payer account pays the world account. */
public record ShopPurchaseAuthorization(boolean allowed, String message, String payerSystemAccountId,
        String payerPluginIdentifier) {
    public static ShopPurchaseAuthorization allowPlayer() { return new ShopPurchaseAuthorization(true, "", "", ""); }
    public static ShopPurchaseAuthorization allowSystem(String accountId) { return allowSystem(accountId, "OZ - Shop"); }
    public static ShopPurchaseAuthorization allowSystem(String accountId, String ownerPluginIdentifier) { return new ShopPurchaseAuthorization(true, "", accountId, ownerPluginIdentifier); }
    public static ShopPurchaseAuthorization deny(String message) { return new ShopPurchaseAuthorization(false, message, "", ""); }
}
