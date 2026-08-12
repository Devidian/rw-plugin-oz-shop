package de.omegazirkel.risingworld.shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.Shop;
import net.risingworld.api.objects.Npc;
import net.risingworld.api.objects.Player;

/** Trader persistence and NPC invariants; Wallet money movement stays in ShopRuntime. */
public final class TraderService {
    private final Connection connection;

    public TraderService(Connection connection) {
        this.connection = connection;
        initialize();
    }

    public synchronized Optional<Trader> register(Npc npc, Player creator) {
        if (npc == null || creator == null || npc.getGlobalID() <= 0L) return Optional.empty();
        long id = npc.getGlobalID();
        Trader existing = find(id).orElse(null);
        String name = npc.getName();
        if (name == null || name.isBlank()) {
            name = "Trader-" + id;
            npc.setName(name);
        }
        applyNpcFlags(npc);
        Trader trader = existing == null
                ? new Trader(id, name, creator.getName(), System.currentTimeMillis(), "default-trader.json", true)
                : new Trader(id, name, existing.createdBy(), existing.createdAt(), existing.systemOffersFile(),
                        existing.pluginShopEnabled());
        save(trader);
        return Optional.of(trader);
    }

    public synchronized Optional<Trader> find(long npcId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT npc_id, name, created_by, created_at, system_offers_file, plugin_shop_enabled
                FROM shop_traders WHERE npc_id = ?
                """)) {
            statement.setLong(1, npcId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not load trader " + npcId + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public synchronized List<Trader> list() {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT npc_id, name, created_by, created_at, system_offers_file, plugin_shop_enabled
                FROM shop_traders ORDER BY name COLLATE NOCASE ASC, npc_id ASC
                """ ); ResultSet result = statement.executeQuery()) {
            java.util.ArrayList<Trader> traders = new java.util.ArrayList<>();
            while (result.next()) traders.add(read(result));
            return traders.stream().sorted(Comparator.comparing(Trader::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Trader::npcId)).toList();
        } catch (SQLException ex) {
            Shop.logger().error("Could not list traders: " + ex.getMessage());
            return List.of();
        }
    }

    public synchronized Optional<Trader> rename(long npcId, String name) {
        Trader old = find(npcId).orElse(null);
        if (old == null || name == null || name.trim().isBlank()) return Optional.empty();
        Trader updated = new Trader(old.npcId(), name, old.createdBy(), old.createdAt(), old.systemOffersFile(),
                old.pluginShopEnabled());
        save(updated);
        return Optional.of(updated);
    }

    public synchronized Optional<Trader> setSystemOffersFile(long npcId, String file) {
        Trader old = find(npcId).orElse(null);
        if (old == null) return Optional.empty();
        Trader updated = new Trader(old.npcId(), old.name(), old.createdBy(), old.createdAt(), file,
                old.pluginShopEnabled());
        save(updated);
        return Optional.of(updated);
    }

    public synchronized Optional<Trader> setPluginShopEnabled(long npcId, boolean enabled) {
        Trader old = find(npcId).orElse(null);
        if (old == null) return Optional.empty();
        Trader updated = new Trader(old.npcId(), old.name(), old.createdBy(), old.createdAt(), old.systemOffersFile(),
                enabled);
        save(updated);
        return Optional.of(updated);
    }

    public synchronized boolean delete(long npcId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shop_traders WHERE npc_id = ?")) {
            statement.setLong(1, npcId);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            Shop.logger().error("Could not remove trader " + npcId + ": " + ex.getMessage());
            return false;
        }
    }

    public void applyNpcFlags(Npc npc) {
        if (npc == null) return;
        npc.setInteractable(true);
        npc.setInvincible(true);
    }

    /** Settles trader-only automatic stock movement before persisting the stock delta. */
    public ShopEconomyStore.ScopeTickResult reconcileEconomy(Trader trader, List<ShopOffer> offers, ShopEconomyStore economy, WalletBridge wallet,
            String currencyIdentifier, boolean dynamicEconomyEnabled, boolean force) {
        if (trader == null || economy == null || wallet == null || currencyIdentifier == null || currencyIdentifier.isBlank())
            return new ShopEconomyStore.ScopeTickResult(trader == null ? "" : trader.economyScope(), List.of(), 0L);
        economy.initializeScope(trader.economyScope());
        boolean scopeDue = economy.scopeTickDue(trader.economyScope());
        if (!force && !scopeDue)
            return new ShopEconomyStore.ScopeTickResult(trader.economyScope(), List.of(), 0L);
        long now = System.currentTimeMillis();
        long scheduledAt = economy.nextScopeTickAt(trader.economyScope());
        List<ShopEconomyStore.OfferTick> changes = new java.util.ArrayList<>();
        for (ShopOffer offer : offers) {
            if (offer == null || !offer.isSystemOffer()) continue;
            ShopEconomyStore.EconomyState state = economy.stateForWithoutTick(trader.economyScope(), offer);
            long stock = state.stock();
            long drain = ShopEconomyStore.automaticDrainEnabled(offer)
                    ? Math.min(automaticDrainBase(offer, stock, state.targetStock()),
                            ShopEconomyStore.targetDrainAmount(state.targetStock(), offer.getDrainPercent(),
                                    offer.getDrainMax(), 1.0d)) : 0L;
            long restock = ShopEconomyStore.automaticRestockEnabled(offer)
                    ? Math.min(Math.max(0L, state.targetStock() - stock),
                            ShopEconomyStore.targetRestockAmount(state.targetStock(), offer.getRestockPercent(),
                                    offer.getRestockMax(), 1.0d)) : 0L;
            if (restock <= 0L && ShopEconomyStore.minimumSystemRestockEnabled(offer, state.refillRate())) {
                restock = Math.min(Math.max(0L, state.targetStock() - stock), 1L);
            }
            if (drain > 0L) {
                long value = DynamicEconomyPricing.outboundValue(offer, state, drain, dynamicEconomyEnabled);
                if (value > 0L && !wallet.transferWorldToSystemIdempotent(trader.accountId(), value,
                        "Trader drain: " + offer.getId(), currencyIdentifier,
                        "OZ - Shop", "trader:" + trader.npcId() + ":drain:" + offer.getId() + ":"
                                + scheduledAt).success()) continue;
                stock -= drain;
            }
            if (restock > 0L) {
                long value = safeValue(restock, offer.getBasePrice());
                if (value > 0L) {
                    WalletBridge.WalletTransferCallResult payment = wallet.transferSystemToSystemIdempotent(
                            trader.accountId(), wallet.worldSystemAccountId(), value, "Trader restock: " + offer.getId(),
                            currencyIdentifier, "OZ - Shop", "trader:" + trader.npcId() + ":restock:" + offer.getId()
                                    + ":" + scheduledAt);
                    if (!payment.success()) restock = 0L;
                }
                stock += restock;
            }
            if (drain > 0L || restock > 0L) {
                if (economy.configure(trader.economyScope(), offer.getId(), stock, state.drainRate(), state.refillRate()))
                    changes.add(new ShopEconomyStore.OfferTick(offer.getId(), state.stock(), stock));
            }
        }
        economy.completeScopeTick(trader.economyScope());
        return new ShopEconomyStore.ScopeTickResult(trader.economyScope(), List.copyOf(changes), now);
    }

    static long automaticDrainBase(ShopOffer offer, long stock, long targetStock) {
        long safeStock = Math.max(0L, stock);
        if (ShopEconomyStore.automaticDrainEnabled(offer) && targetStock > 0L) {
            return Math.max(0L, safeStock - targetStock);
        }
        return 0L;
    }

    public static boolean hasSufficientBasePayoutBalance(long traderBalance, long basePayout) {
        return Math.max(0L, traderBalance) >= Math.max(0L, basePayout);
    }

    private static long safeValue(long amount, double basePrice) {
        if (amount <= 0L || basePrice <= 0.0d || !Double.isFinite(basePrice)) return 0L;
        double value = amount * basePrice;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) Math.floor(value));
    }

    private void initialize() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_traders (
                        npc_id BIGINT PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_by TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        system_offers_file TEXT NOT NULL DEFAULT 'default-trader.json',
                        plugin_shop_enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """);
        } catch (SQLException ex) {
            Shop.logger().error("Could not initialize trader database: " + ex.getMessage());
        }
    }

    private void save(Trader trader) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_traders(npc_id, name, created_by, created_at, system_offers_file, plugin_shop_enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(npc_id) DO UPDATE SET name=excluded.name, system_offers_file=excluded.system_offers_file,
                    plugin_shop_enabled=excluded.plugin_shop_enabled
                """)) {
            statement.setLong(1, trader.npcId());
            statement.setString(2, trader.name());
            statement.setString(3, trader.createdBy());
            statement.setLong(4, trader.createdAt());
            statement.setString(5, trader.systemOffersFile());
            statement.setInt(6, trader.pluginShopEnabled() ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not save trader " + trader.npcId() + ": " + ex.getMessage());
        }
    }

    private static Trader read(ResultSet result) throws SQLException {
        return new Trader(result.getLong("npc_id"), result.getString("name"), result.getString("created_by"),
                result.getLong("created_at"), result.getString("system_offers_file"),
                result.getInt("plugin_shop_enabled") != 0);
    }
}
