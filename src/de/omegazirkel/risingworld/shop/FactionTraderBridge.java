package de.omegazirkel.risingworld.shop;

import de.omegazirkel.risingworld.tools.bridge.FactionBridge;
import net.risingworld.api.Plugin;

/** Fails closed when Factions or its trader payout facade is unavailable. */
public final class FactionTraderBridge {
    private final Plugin owner;
    private final FactionBridge factions;

    public FactionTraderBridge(Plugin owner) {
        this.owner = owner;
        this.factions = new FactionBridge(owner);
    }

    public Integer factionIdFor(int playerDbId) { return factions.factionIdForPlayer(playerDbId); }
    public boolean canManage(int playerDbId) { return factions.canManageTrader(playerDbId); }
    public Integer capacity(int factionId) { return factions.traderLicenses(factionId); }
    public String accountId(int factionId) { return factions.factionAccountId(factionId); }

    public String factionName(int factionId) {
        Object result = invoke("getFactionName", new Class<?>[] { int.class }, factionId);
        return result instanceof String value && !value.isBlank() ? value : null;
    }

    public boolean paySeller(int factionId, int playerDbId, long amount, String currency, String correlationId) {
        return Boolean.TRUE.equals(invoke("payFactionTraderSeller",
                new Class<?>[] { int.class, int.class, long.class, String.class, String.class },
                factionId, playerDbId, amount, currency, correlationId));
    }

    public boolean reversePayment(int factionId, String correlationId) {
        return Boolean.TRUE.equals(invoke("reverseFactionTraderPayment",
                new Class<?>[] { int.class, String.class }, factionId, correlationId));
    }

    private Object invoke(String name, Class<?>[] types, Object... values) {
        Plugin plugin = owner.getPluginByName("OZ - Factions");
        if (plugin == null) return null;
        try { return plugin.getClass().getMethod(name, types).invoke(plugin, values); }
        catch (ReflectiveOperationException | LinkageError ex) {
            de.omegazirkel.risingworld.Shop.logger().error("Faction trader API " + name + " failed: " + ex.getMessage());
            return null;
        }
    }
}
