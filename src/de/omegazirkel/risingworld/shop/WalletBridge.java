package de.omegazirkel.risingworld.shop;

import java.lang.reflect.Field;

import net.risingworld.api.Plugin;

/** Shop-specific compatibility name for the shared OZ Tools Wallet bridge. */
public class WalletBridge extends de.omegazirkel.risingworld.tools.bridge.WalletBridge {
    private final Plugin owner;

    public WalletBridge(Plugin owner) {
        super(owner);
        this.owner = owner;
    }

    public WalletCallResult creditSystemAccountIdempotent(String accountId, long value, String reason,
            String currencyIdentifier, String pluginIdentifier, String correlationId) {
        Plugin wallet = owner == null ? null : owner.getPluginByName("OZ - Wallet");
        if (wallet == null) return new WalletCallResult(false, "OZ - Wallet is not available.");
        try {
            Object result = wallet.getClass().getMethod("creditSystemAccountIdempotent", String.class, long.class,
                    String.class, String.class, String.class, String.class).invoke(wallet, accountId, value, reason,
                            currencyIdentifier, pluginIdentifier, correlationId);
            Field success = result.getClass().getField("success");
            Field message = result.getClass().getField("message");
            return new WalletCallResult(Boolean.TRUE.equals(success.get(result)), String.valueOf(message.get(result)));
        } catch (ReflectiveOperationException ex) {
            return new WalletCallResult(false, "Wallet system-account issuance API is not available.");
        }
    }
}
