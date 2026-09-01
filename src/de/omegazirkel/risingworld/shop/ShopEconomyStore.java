package de.omegazirkel.risingworld.shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.LongSupplier;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.Shop;

public class ShopEconomyStore {
    private static final String GLOBAL_SCOPE = "global";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    public record EconomyState(long stock, long targetStock, long stockLimit, double drainRate, double refillRate) {
        public boolean limited() {
            return stockLimit > 0L;
        }
    }

    public record EconomyTickStatus(long lastTickAt, long nextDrainAt, long nextRestockAt, boolean automatic,
            boolean active) {
        public static EconomyTickStatus inactive() {
            return new EconomyTickStatus(0L, 0L, 0L, false, false);
        }
    }

    public record EconomyCheck(boolean allowed, String messageKey) {
        public static EconomyCheck ok() {
            return new EconomyCheck(true, "");
        }

        public static EconomyCheck rejected(String messageKey) {
            return new EconomyCheck(false, messageKey == null ? "" : messageKey);
        }
    }

    public record EconomyUpdate(
            Long targetStock,
            Long stockLimit,
            String stockMode,
            Double minPriceMultiplier,
            Double maxPriceMultiplier,
            Double spreadPercent,
            Double drainPercent,
            Long drainMax,
            Double restockPercent,
            Long restockMax,
            Long perPlayerDailySellLimit,
            Long globalDailySellLimit) {
    }

    /** A committed, scope-wide stock reconciliation suitable for an operator report. */
    public record ScopeTickResult(String scope, List<OfferTick> changes, long completedAt) {
        public boolean changed() { return changes != null && !changes.isEmpty(); }
    }

    public record OfferTick(String offerId, long previousStock, long stock) { }

    private final Connection connection;
    private final LongSupplier clock;
    private long tickIntervalMillis = ONE_HOUR_MILLIS;

    public ShopEconomyStore(Connection connection) {
        this(connection, System::currentTimeMillis);
    }

    ShopEconomyStore(Connection connection, LongSupplier clock) {
        this.connection = connection;
        this.clock = clock;
        initialize();
    }

    public void setTickIntervalHours(int hours) {
        tickIntervalMillis = Math.max(1, hours) * ONE_HOUR_MILLIS;
    }

    /** Creates a missing cadence row without moving an established scope. */
    public void initializeScope(String scope) {
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        if (scopeLastTick(effectiveScope) <= 0L) {
            long legacyTick = maxOfferTick(effectiveScope);
            storeScopeTick(effectiveScope, legacyTick > 0L ? legacyTick : clock.getAsLong());
        }
    }

    public boolean scopeTickDue(String scope) {
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        initializeScope(effectiveScope);
        return clock.getAsLong() - scopeLastTick(effectiveScope) >= tickIntervalMillis;
    }

    public void requestImmediateScopeTick(String scope) {
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        storeScopeTick(effectiveScope, clock.getAsLong() - tickIntervalMillis);
    }

    public void completeScopeTick(String scope) {
        storeScopeTick(scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim(), clock.getAsLong());
    }

    public List<ScopeTickResult> reconcile(Collection<ShopOffer> offers, Collection<ShopZone> zones) {
        return reconcile(offers, zones, false);
    }

    /** Runs every applicable scope immediately when an administrator requests a tick. */
    public List<ScopeTickResult> reconcileNow(Collection<ShopOffer> offers, Collection<ShopZone> zones) {
        return reconcile(offers, zones, true);
    }

    private List<ScopeTickResult> reconcile(Collection<ShopOffer> offers, Collection<ShopZone> zones, boolean force) {
        if (offers == null || offers.isEmpty()) {
            return List.of();
        }
        for (ShopOffer offer : offers) {
            if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
                continue;
            }
            ensureOfferState(GLOBAL_SCOPE, offer);
            if (zones == null) {
                continue;
            }
            for (ShopZone zone : zones) {
                if (zone != null && zone.getAreaId() > 0L) {
                    ensureOfferState(scope(zone), offer);
                }
            }
        }
        initializeScope(GLOBAL_SCOPE);
        if (zones != null) for (ShopZone zone : zones) if (zone != null && zone.getAreaId() > 0L) initializeScope(scope(zone));
        List<ScopeTickResult> results = new ArrayList<>();
        ScopeTickResult global = reconcileScope(GLOBAL_SCOPE, offers, force);
        if (global.changed()) results.add(global);
        if (zones != null) for (ShopZone zone : zones) {
            if (zone != null && zone.getAreaId() > 0L) {
                ScopeTickResult result = reconcileScope(scope(zone), offers, force);
                if (result.changed()) results.add(result);
            }
        }
        return List.copyOf(results);
    }

    public void recordSystemSale(String scope, ShopOffer offer, long value) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return;
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        long amount = Math.max(0, offer.getAmount());
        long stockLimit = Math.max(0L, offer.getDefaultStockLimit());
        long now = clock.getAsLong();
        try (PreparedStatement state = connection.prepareStatement("""
                UPDATE shop_offer_economy_state
                SET stock = CASE WHEN ? > 0 OR stock > 0 THEN MAX(0, stock - ?) ELSE stock END,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """);
                PreparedStatement stats = connection.prepareStatement("""
                UPDATE shop_offer_trade_stats
                SET sold_amount = sold_amount + ?,
                    sold_value = sold_value + ?,
                    last_trade_at = ?,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """)) {
            state.setLong(1, stockLimit);
            state.setLong(2, amount);
            state.setLong(3, now);
            state.setString(4, effectiveScope);
            state.setString(5, offer.getId());
            state.executeUpdate();

            stats.setLong(1, amount);
            stats.setLong(2, Math.max(0L, value));
            stats.setLong(3, now);
            stats.setLong(4, now);
            stats.setString(5, effectiveScope);
            stats.setString(6, offer.getId());
            stats.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not record shop sale for " + effectiveScope + "/" + offer.getId() + ": "
                    + ex.getMessage());
        }
    }

    private void recordSystemSaleStats(String scope, ShopOffer offer, long value) {
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        long amount = Math.max(0, offer.getAmount());
        long now = clock.getAsLong();
        try (PreparedStatement stats = connection.prepareStatement("""
                UPDATE shop_offer_trade_stats
                SET sold_amount = sold_amount + ?,
                    sold_value = sold_value + ?,
                    last_trade_at = ?,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """)) {
            stats.setLong(1, amount);
            stats.setLong(2, Math.max(0L, value));
            stats.setLong(3, now);
            stats.setLong(4, now);
            stats.setString(5, effectiveScope);
            stats.setString(6, offer.getId());
            stats.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not record shop sale stats for " + effectiveScope + "/" + offer.getId()
                    + ": " + ex.getMessage());
        }
    }

    public boolean canSellToPlayer(String scope, ShopOffer offer) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return true;
        }
        if (offer.getStockMode() == ShopStockMode.STATIC) {
            return true;
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock
                FROM shop_offer_economy_state
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setString(1, effectiveScope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return true;
                }
                long stock = result.getLong("stock");
                long stockLimit = Math.max(0L, offer.getDefaultStockLimit());
                return stockLimit <= 0L || stock >= offer.getAmount();
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not check shop stock for " + effectiveScope + "/" + offer.getId() + ": "
                    + ex.getMessage());
            return false;
        }
    }

    public EconomyCheck canBuyFromPlayer(String scope, long playerId, ShopOffer offer) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return EconomyCheck.ok();
        }
        if (!offer.canPlayerSellToSystem()) {
            return EconomyCheck.rejected("tc.shop.dynamic.stock.full");
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock
                FROM shop_offer_economy_state
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setString(1, effectiveScope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return EconomyCheck.ok();
                }
                long stock = result.getLong("stock");
                long stockLimit = Math.max(0L, offer.getDefaultStockLimit());
                if (stockLimit > 0L && stock + Math.max(0, offer.getAmount()) > stockLimit) {
                    return EconomyCheck.rejected("tc.shop.dynamic.stock.full");
                }
                long amount = Math.max(0L, offer.getAmount());
                long dayStart = dayStart(System.currentTimeMillis());
                if (offer.usesPlayerSellLimits() && offer.getGlobalDailySellLimit() > 0L
                        && counterAmount(effectiveScope, offer.getId(), "global", dayStart) + amount
                                > offer.getGlobalDailySellLimit()) {
                    return EconomyCheck.rejected("tc.shop.daily.sell.limit.global");
                }
                if (offer.usesPlayerSellLimits() && offer.getPerPlayerDailySellLimit() > 0L
                        && counterAmount(effectiveScope, offer.getId(), playerKey(playerId), dayStart) + amount
                                > offer.getPerPlayerDailySellLimit()) {
                    return EconomyCheck.rejected("tc.shop.daily.sell.limit.player");
                }
                return EconomyCheck.ok();
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not check shop buy stock for " + effectiveScope + "/" + offer.getId() + ": "
                    + ex.getMessage());
            return EconomyCheck.rejected("tc.shop.stock.update.failed");
        }
    }

    public EconomyState stateFor(String scope, ShopOffer offer) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return new EconomyState(0L, 0L, 0L, 0.0d, 0.0d);
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock
                FROM shop_offer_economy_state
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setString(1, effectiveScope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new EconomyState(
                            result.getLong("stock"),
                            offer.getDefaultTargetStock(),
                            offer.getDefaultStockLimit(),
                            offer.getDefaultDrainRate(),
                            offer.getDefaultRefillRate());
                }
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not read shop economy state for " + effectiveScope + "/" + offer.getId()
                    + ": " + ex.getMessage());
        }
        return new EconomyState(offer.getDefaultStock(), offer.getDefaultTargetStock(), offer.getDefaultStockLimit(),
                offer.getDefaultDrainRate(), offer.getDefaultRefillRate());
    }

    /** Reads trader economy state without applying the normal system-shop automatic tick. */
    public EconomyState stateForWithoutTick(String scope, ShopOffer offer) {
        if (offer == null || !offer.isSystemOffer()) return new EconomyState(0L, 0L, 0L, 0.0d, 0.0d);
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock FROM shop_offer_economy_state WHERE scope = ? AND offer_id = ?
                """)) {
            statement.setString(1, effectiveScope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return new EconomyState(result.getLong("stock"), offer.getDefaultTargetStock(),
                        offer.getDefaultStockLimit(), offer.getDefaultDrainRate(), offer.getDefaultRefillRate());
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not read trader economy state: " + ex.getMessage());
        }
        return new EconomyState(offer.getDefaultStock(), offer.getDefaultTargetStock(), offer.getDefaultStockLimit(),
                offer.getDefaultDrainRate(), offer.getDefaultRefillRate());
    }

    public EconomyTickStatus tickStatusFor(String scope, ShopOffer offer) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return EconomyTickStatus.inactive();
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        boolean automatic = automaticTicksEnabled(offer);
        long now = clock.getAsLong();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock, last_tick_at
                FROM shop_offer_economy_state
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setString(1, effectiveScope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new EconomyTickStatus(0L, 0L, 0L, automatic, false);
                }
                long stock = result.getLong("stock");
                long targetStock = offer.getDefaultTargetStock();
                long stockLimit = offer.getDefaultStockLimit();
                double drainRate = offer.getDefaultDrainRate();
                double refillRate = offer.getDefaultRefillRate();
                long lastTickAt = result.getLong("last_tick_at");
                if (targetStock <= 0L && stockLimit > 0L) {
                    targetStock = stockLimit;
                }
                if (targetStock <= 0L && stock > 0L) {
                    targetStock = stock;
                }
                long baseline = lastTickAt > 0L ? lastTickAt : now;
                long nextDrainAt = automaticDrainEnabled(offer)
                        ? nextEconomyTickAt(baseline, targetStock, offer.getDrainPercent(), drainRate)
                        : 0L;
                long nextRestockAt = automaticRestockEnabled(offer)
                        ? nextEconomyTickAt(baseline, targetStock, offer.getRestockPercent(), refillRate)
                        : 0L;
                if (nextRestockAt <= 0L && minimumSystemRestockEnabled(offer, refillRate)) {
                    nextRestockAt = baseline + tickIntervalMillis;
                }
                boolean active = automatic && (nextDrainAt > 0L || nextRestockAt > 0L);
                return new EconomyTickStatus(lastTickAt, automatic ? nextDrainAt : 0L,
                        automatic ? nextRestockAt : 0L, automatic, active);
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not read shop economy tick status for " + effectiveScope + "/"
                    + offer.getId() + ": " + ex.getMessage());
            return new EconomyTickStatus(0L, 0L, 0L, automatic, false);
        }
    }

    /** Shared scope cadence for UI; it remains meaningful even when an offer has no stock movement configured. */
    public long nextScopeTickAt(String scope) {
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        long last = scopeLastTick(effectiveScope);
        return (last > 0L ? last : clock.getAsLong()) + tickIntervalMillis;
    }

    public boolean configure(String scope, String offerId, long stock, double drainRate, double refillRate) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        String normalizedOfferId = offerId.trim().toLowerCase();
        ensureOfferState(effectiveScope, normalizedOfferId, 0L);
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shop_offer_economy_state
                SET stock = ?,
                    last_tick_at = ?,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setLong(1, Math.max(0L, stock));
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, effectiveScope);
            statement.setString(5, normalizedOfferId);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            Shop.logger().error("Could not configure shop economy state for " + effectiveScope + "/"
                    + normalizedOfferId + ": " + ex.getMessage());
            return false;
        }
    }

    public int resetStocksToTarget(String scope, Collection<ShopOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return 0;
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        int updated = 0;
        for (ShopOffer offer : offers) {
            if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
                continue;
            }
            ensureOfferState(effectiveScope, offer);
            EconomyState state = stateFor(effectiveScope, offer);
            long targetStock = state.targetStock() > 0L ? state.targetStock() : offer.getDefaultTargetStock();
            if (configure(effectiveScope, offer.getId(), Math.max(0L, targetStock),
                    state.drainRate(), state.refillRate())) {
                updated++;
            }
        }
        return updated;
    }

    /** Removes all stock, statistics, and limits owned by a dissolved trader scope. */
    public boolean deleteScope(String scope) {
        if (scope == null || scope.isBlank()) return false;
        try (PreparedStatement state = connection.prepareStatement("DELETE FROM shop_offer_economy_state WHERE scope = ?");
                PreparedStatement stats = connection.prepareStatement("DELETE FROM shop_offer_trade_stats WHERE scope = ?");
                PreparedStatement counters = connection.prepareStatement("DELETE FROM shop_offer_daily_sell_counters WHERE scope = ?")) {
            state.setString(1, scope);
            stats.setString(1, scope);
            counters.setString(1, scope);
            state.executeUpdate();
            stats.executeUpdate();
            counters.executeUpdate();
            return true;
        } catch (SQLException ex) {
            Shop.logger().error("Could not remove shop economy scope " + scope + ": " + ex.getMessage());
            return false;
        }
    }

    /** Removes one offer's persisted state without affecting other offers in the same shop scope. */
    public boolean deleteOffer(String scope, String offerId) {
        if (scope == null || scope.isBlank() || offerId == null || offerId.isBlank()) return false;
        try (PreparedStatement state = connection.prepareStatement("DELETE FROM shop_offer_economy_state WHERE scope = ? AND offer_id = ?");
                PreparedStatement stats = connection.prepareStatement("DELETE FROM shop_offer_trade_stats WHERE scope = ? AND offer_id = ?");
                PreparedStatement counters = connection.prepareStatement("DELETE FROM shop_offer_daily_sell_counters WHERE scope = ? AND offer_id = ?")) {
            for (PreparedStatement statement : new PreparedStatement[] { state, stats, counters }) {
                statement.setString(1, scope); statement.setString(2, offerId); statement.executeUpdate();
            }
            return true;
        } catch (SQLException ex) {
            Shop.logger().error("Could not remove shop offer economy state " + scope + "/" + offerId + ": " + ex.getMessage());
            return false;
        }
    }

    public boolean configureEconomy(String scope, ShopOffer offer, EconomyUpdate update) {
        return false;
    }

    public ShopOffer configuredOffer(String scope, ShopOffer offer) {
        return offer;
    }

    public void recordSystemBuy(String scope, long playerId, ShopOffer offer, long value) {
        if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
            return;
        }
        String effectiveScope = scope == null || scope.isBlank() ? GLOBAL_SCOPE : scope.trim();
        ensureOfferState(effectiveScope, offer);
        applyTick(effectiveScope, offer);
        long amount = Math.max(0, offer.getAmount());
        long stockLimit = Math.max(0L, offer.getDefaultStockLimit());
        long now = System.currentTimeMillis();
        try (PreparedStatement state = connection.prepareStatement("""
                UPDATE shop_offer_economy_state
                SET stock = CASE
                        WHEN ? > 0 THEN MIN(?, stock + ?)
                        ELSE stock + ?
                    END,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """);
                PreparedStatement stats = connection.prepareStatement("""
                UPDATE shop_offer_trade_stats
                SET bought_amount = bought_amount + ?,
                    bought_value = bought_value + ?,
                    last_trade_at = ?,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """)) {
            state.setLong(1, stockLimit);
            state.setLong(2, stockLimit);
            state.setLong(3, amount);
            state.setLong(4, amount);
            state.setLong(5, now);
            state.setString(6, effectiveScope);
            state.setString(7, offer.getId());
            state.executeUpdate();

            stats.setLong(1, amount);
            stats.setLong(2, Math.max(0L, value));
            stats.setLong(3, now);
            stats.setLong(4, now);
            stats.setString(5, effectiveScope);
            stats.setString(6, offer.getId());
            stats.executeUpdate();
            recordDailySellCounter(effectiveScope, offer.getId(), "global", amount, Math.max(0L, value), now);
            recordDailySellCounter(effectiveScope, offer.getId(), playerKey(playerId), amount, Math.max(0L, value), now);
        } catch (SQLException ex) {
            Shop.logger().error("Could not record shop buy for " + effectiveScope + "/" + offer.getId() + ": "
                    + ex.getMessage());
        }
    }

    private void initialize() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_offer_economy_state (
                        scope TEXT NOT NULL,
                        offer_id TEXT NOT NULL,
                        stock BIGINT NOT NULL DEFAULT 0,
                        target_stock BIGINT NOT NULL DEFAULT 0,
                        stock_limit BIGINT NOT NULL DEFAULT 0,
                        drain_rate REAL NOT NULL DEFAULT 0,
                        refill_rate REAL NOT NULL DEFAULT 0,
                        stock_mode TEXT NOT NULL DEFAULT '',
                        min_price_multiplier REAL NOT NULL DEFAULT -1,
                        max_price_multiplier REAL NOT NULL DEFAULT -1,
                        spread_percent REAL NOT NULL DEFAULT -1,
                        drain_percent REAL NOT NULL DEFAULT -1,
                        drain_max BIGINT NOT NULL DEFAULT -1,
                        restock_percent REAL NOT NULL DEFAULT -1,
                        restock_max BIGINT NOT NULL DEFAULT -1,
                        per_player_daily_sell_limit BIGINT NOT NULL DEFAULT -1,
                        global_daily_sell_limit BIGINT NOT NULL DEFAULT -1,
                        economy_configured INTEGER NOT NULL DEFAULT 0,
                        last_tick_at BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY(scope, offer_id)
                    );
                    """);
            migrateEconomyStateColumns();
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_offer_trade_stats (
                        scope TEXT NOT NULL,
                        offer_id TEXT NOT NULL,
                        bought_amount BIGINT NOT NULL DEFAULT 0,
                        sold_amount BIGINT NOT NULL DEFAULT 0,
                        bought_value BIGINT NOT NULL DEFAULT 0,
                        sold_value BIGINT NOT NULL DEFAULT 0,
                        last_trade_at BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY(scope, offer_id)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_offer_daily_sell_counters (
                        scope TEXT NOT NULL,
                        offer_id TEXT NOT NULL,
                        player_id TEXT NOT NULL,
                        day_start BIGINT NOT NULL,
                        sold_amount BIGINT NOT NULL DEFAULT 0,
                        sold_value BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY(scope, offer_id, player_id, day_start)
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_economy_scope_ticks (
                        scope TEXT PRIMARY KEY,
                        last_tick_at BIGINT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL DEFAULT 0
                    )
                    """);
        } catch (SQLException ex) {
            Shop.logger().error("Could not initialize shop economy database: " + ex.getMessage());
        }
    }

    public void applyTicks(Collection<ShopOffer> offers, Collection<ShopZone> zones) {
        if (offers == null || offers.isEmpty()) {
            return;
        }
        for (ShopOffer offer : offers) {
            if (offer == null || offer.getId() == null || offer.getId().isBlank() || !offer.isSystemOffer()) {
                continue;
            }
            applyTick(GLOBAL_SCOPE, offer);
            if (zones == null) {
                continue;
            }
            for (ShopZone zone : zones) {
                if (zone != null && zone.getAreaId() > 0L) {
                    String scope = scope(zone);
                    applyTick(scope, offer);
                }
            }
        }
    }

    /**
     * Computes and stores all due offer changes for one scope in one SQLite
     * transaction. Scope ticks deliberately replace opportunistic per-offer
     * reads so a report can never describe a partially persisted scope.
     */
    private ScopeTickResult reconcileScope(String scope, Collection<ShopOffer> offers, boolean force) {
        if (offers == null || offers.isEmpty()) return new ScopeTickResult(scope, List.of(), 0L);
        long now = clock.getAsLong();
        long lastTick = scopeLastTick(scope);
        if (lastTick <= 0L) {
            lastTick = maxOfferTick(scope);
            if (lastTick <= 0L) {
                storeScopeTick(scope, now);
                return new ScopeTickResult(scope, List.of(), now);
            }
            storeScopeTick(scope, lastTick);
        }
        long elapsed = now - lastTick;
        if (!force && elapsed < tickIntervalMillis) return new ScopeTickResult(scope, List.of(), lastTick);
        double elapsedHours = force ? Math.max(1.0d, tickIntervalMillis / (double) ONE_HOUR_MILLIS)
                : elapsed / (double) ONE_HOUR_MILLIS;
        List<OfferTick> changes = new ArrayList<>();
        for (ShopOffer offer : offers) {
            if (offer == null || !offer.isSystemOffer() || !automaticTicksEnabled(offer)) continue;
            EconomyState state = stateForWithoutTick(scope, offer);
            long next = reconciledStock(offer, state, elapsedHours);
            if (next != state.stock()) changes.add(new OfferTick(offer.getId(), state.stock(), next));
        }
        if (changes.isEmpty()) {
            storeScopeTick(scope, now);
            return new ScopeTickResult(scope, List.of(), now);
        }
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE shop_offer_economy_state SET stock = ?, last_tick_at = ?, updated_at = ?
                    WHERE scope = ? AND offer_id = ?
                    """)) {
                for (OfferTick change : changes) {
                    update.setLong(1, change.stock()); update.setLong(2, now); update.setLong(3, now);
                    update.setString(4, scope); update.setString(5, change.offerId()); update.addBatch();
                }
                update.executeBatch();
            }
            storeScopeTick(scope, now);
            connection.commit();
            return new ScopeTickResult(scope, List.copyOf(changes), now);
        } catch (SQLException | RuntimeException ex) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            Shop.logger().error("Could not reconcile economy scope " + scope + ": " + ex.getMessage());
            return new ScopeTickResult(scope, List.of(), lastTick);
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
        }
    }

    private long reconciledStock(ShopOffer offer, EconomyState state, double elapsedHours) {
        long stock = state.stock(); long target = state.targetStock() > 0L ? state.targetStock()
                : state.stockLimit() > 0L ? state.stockLimit() : stock;
        boolean drainEnabled = automaticDrainEnabled(offer); boolean refillEnabled = automaticRestockEnabled(offer);
        long drain = drainEnabled && target > 0L && offer.getDrainPercent() > 0.0d
                ? targetDrainAmount(target, offer.getDrainPercent(), offer.getDrainMax(), elapsedHours)
                : drainEnabled ? (long) Math.floor(state.drainRate() * elapsedHours) : 0L;
        long refill = refillEnabled && target > 0L && offer.getRestockPercent() > 0.0d
                ? targetRestockAmount(target, offer.getRestockPercent(), offer.getRestockMax(), elapsedHours)
                : refillEnabled ? (long) Math.floor(state.refillRate() * elapsedHours) : 0L;
        if (refill <= 0L && refillEnabled && minimumSystemRestockEnabled(offer, state.refillRate()) && elapsedHours >= 1.0d)
            refill = Math.max(1L, (long) Math.floor(elapsedHours));
        long next = Math.max(0L, stock - Math.min(drain, Math.max(0L, stock - target)));
        if (refill > 0L && (target <= 0L || next < target)) next = target > 0L ? Math.min(target, next + refill) : next + refill;
        return state.stockLimit() > 0L ? Math.min(state.stockLimit(), next) : next;
    }

    private long scopeLastTick(String scope) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_tick_at FROM shop_economy_scope_ticks WHERE scope = ?")) {
            statement.setString(1, scope); try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        } catch (SQLException ex) { return 0L; }
    }

    private long maxOfferTick(String scope) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(last_tick_at) FROM shop_offer_economy_state WHERE scope = ?")) {
            statement.setString(1, scope); try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        } catch (SQLException ex) { return 0L; }
    }

    private void storeScopeTick(String scope, long tick) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_economy_scope_ticks(scope, last_tick_at, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(scope) DO UPDATE SET last_tick_at = excluded.last_tick_at, updated_at = excluded.updated_at
                """)) { statement.setString(1, scope); statement.setLong(2, tick); statement.setLong(3, tick); statement.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException("Could not store economy scope tick", ex); }
    }

    private void applyTick(String scope, ShopOffer offer) {
        if (!automaticTicksEnabled(offer)) {
            return;
        }
        long now = clock.getAsLong();
        long stock;
        long targetStock;
        long stockLimit;
        double drainRate;
        double refillRate;
        long lastTickAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT stock, last_tick_at
                FROM shop_offer_economy_state
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setString(1, scope);
            statement.setString(2, offer.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return;
                }
                stock = result.getLong("stock");
                targetStock = offer.getDefaultTargetStock();
                stockLimit = offer.getDefaultStockLimit();
                drainRate = offer.getDefaultDrainRate();
                refillRate = offer.getDefaultRefillRate();
                lastTickAt = result.getLong("last_tick_at");
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not read shop economy tick state for " + scope + "/" + offer.getId() + ": "
                    + ex.getMessage());
            return;
        }

        if (targetStock <= 0L && stockLimit > 0L) {
            targetStock = stockLimit;
        }
        if (targetStock <= 0L && stock > 0L) {
            targetStock = stock;
        }
        boolean automaticDrain = automaticDrainEnabled(offer);
        boolean automaticRestock = automaticRestockEnabled(offer);
        boolean minimumSystemRestock = automaticRestock && minimumSystemRestockEnabled(offer, refillRate);
        if ((!automaticDrain || drainRate <= 0.0d) && (!automaticRestock || refillRate <= 0.0d)
                && (!automaticDrain || offer.getDrainPercent() <= 0.0d)
                && (!automaticRestock || offer.getRestockPercent() <= 0.0d) && !minimumSystemRestock) {
            return;
        }
        if (lastTickAt <= 0L) {
            updateTick(scope, offer.getId(), stock, now);
            return;
        }
        long elapsedMillis = now - lastTickAt;
        if (elapsedMillis < tickIntervalMillis) {
            return;
        }
        double elapsedHours = elapsedMillis / (double) ONE_HOUR_MILLIS;
        long drain = automaticDrain && targetStock > 0L && offer.getDrainPercent() > 0.0d
                ? targetDrainAmount(targetStock, offer.getDrainPercent(), offer.getDrainMax(), elapsedHours)
                : automaticDrain ? (long) Math.floor(drainRate * elapsedHours) : 0L;
        long refill = automaticRestock && targetStock > 0L && offer.getRestockPercent() > 0.0d
                ? targetRestockAmount(targetStock, offer.getRestockPercent(), offer.getRestockMax(), elapsedHours)
                : (long) Math.floor(refillRate * elapsedHours);
        if (!automaticRestock) {
            refill = 0L;
        }
        if (refill <= 0L && minimumSystemRestock && elapsedHours >= 1.0d) {
            refill = Math.max(1L, (long) Math.floor(elapsedHours));
        }
        if (drain == 0L && refill == 0L) {
            return;
        }
        long drainCeiling = Math.max(0L, stock - targetStock);
        long newStock = Math.max(0L, stock - Math.min(drain, drainCeiling));
        long refillCeiling = targetStock > 0L ? targetStock : stockLimit;
        if (refill > 0L && (refillCeiling <= 0L || newStock < refillCeiling)) {
            newStock = refillCeiling > 0L ? Math.min(refillCeiling, newStock + refill) : newStock + refill;
        }
        if (stockLimit > 0L) {
            newStock = Math.min(stockLimit, newStock);
        }
        updateTick(scope, offer.getId(), newStock, now);
    }

    static boolean automaticTicksEnabled(ShopOffer offer) {
        if (offer == null) {
            return false;
        }
        return offer.getStockMode() == ShopStockMode.LOOT
                || offer.getStockMode() == ShopStockMode.SYSTEM_SUPPLIED
                || offer.getStockMode() == ShopStockMode.HYBRID;
    }

    static boolean automaticDrainEnabled(ShopOffer offer) {
        return offer != null && (offer.getStockMode() == ShopStockMode.LOOT
                || offer.getStockMode() == ShopStockMode.HYBRID);
    }

    static boolean automaticRestockEnabled(ShopOffer offer) {
        return offer != null && (offer.getStockMode() == ShopStockMode.SYSTEM_SUPPLIED
                || offer.getStockMode() == ShopStockMode.HYBRID);
    }

    static boolean minimumSystemRestockEnabled(ShopOffer offer, double legacyRate) {
        return offer != null
                && offer.getStockMode() == ShopStockMode.SYSTEM_SUPPLIED
                && offer.getRestockPercent() <= 0.0d
                && offer.getRestockMax() <= 0L
                && legacyRate <= 0.0d;
    }

    static long targetDrainAmount(long targetStock, double percent, long max, double elapsedHours) {
        if (targetStock <= 0L || percent <= 0.0d || elapsedHours < 1.0d) {
            return 0L;
        }
        double raw = targetStock * (percent / 100.0d) * elapsedHours;
        double capped = max > 0L ? Math.min(raw, max * elapsedHours) : raw;
        return (long) Math.floor(capped);
    }

    static long targetRestockAmount(long targetStock, double percent, long max, double elapsedHours) {
        if (targetStock <= 0L || percent <= 0.0d || elapsedHours < 1.0d) {
            return 0L;
        }
        double raw = targetStock * (percent / 100.0d) * elapsedHours;
        long rounded = (long) Math.floor(raw);
        if (max > 0L) {
            return Math.min(rounded, (long) Math.floor(max * elapsedHours));
        }
        return rounded;
    }

    private long nextEconomyTickAt(long baseline, long targetStock, double percent, double legacyRate) {
        if (baseline <= 0L) {
            return 0L;
        }
        if ((targetStock > 0L && percent > 0.0d) || legacyRate > 0.0d) {
            return baseline + tickIntervalMillis;
        }
        return 0L;
    }

    private void updateTick(String scope, String offerId, long stock, long now) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shop_offer_economy_state
                SET stock = ?,
                    last_tick_at = ?,
                    updated_at = ?
                WHERE scope = ? AND offer_id = ?;
                """)) {
            statement.setLong(1, stock);
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, scope);
            statement.setString(5, offerId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not update shop economy tick state for " + scope + "/" + offerId + ": "
                    + ex.getMessage());
        }
    }

    private long counterAmount(String scope, String offerId, String playerId, long dayStart) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sold_amount
                FROM shop_offer_daily_sell_counters
                WHERE scope = ? AND offer_id = ? AND player_id = ? AND day_start = ?;
                """)) {
            statement.setString(1, scope);
            statement.setString(2, offerId);
            statement.setString(3, playerId);
            statement.setLong(4, dayStart);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.max(0L, result.getLong("sold_amount")) : 0L;
            }
        } catch (SQLException ex) {
            Shop.logger().error("Could not read shop daily sell counter for " + scope + "/" + offerId + "/"
                    + playerId + ": " + ex.getMessage());
            return Long.MAX_VALUE;
        }
    }

    private void recordDailySellCounter(String scope, String offerId, String playerId, long amount, long value, long now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_offer_daily_sell_counters(
                    scope, offer_id, player_id, day_start, sold_amount, sold_value, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(scope, offer_id, player_id, day_start) DO UPDATE SET
                    sold_amount = sold_amount + excluded.sold_amount,
                    sold_value = sold_value + excluded.sold_value,
                    updated_at = excluded.updated_at;
                """)) {
            statement.setString(1, scope);
            statement.setString(2, offerId);
            statement.setString(3, playerId);
            statement.setLong(4, dayStart(now));
            statement.setLong(5, Math.max(0L, amount));
            statement.setLong(6, Math.max(0L, value));
            statement.setLong(7, now);
            statement.executeUpdate();
        }
    }

    private void ensureOfferState(String scope, ShopOffer offer) {
        ensureOfferState(scope, offer.getId(), offer.getDefaultStock(), offer.getDefaultDrainRate(),
                offer.getDefaultRefillRate(), offer.getDefaultTargetStock(), offer.getDefaultStockLimit(), offer);
    }

    private void ensureOfferState(String scope, String offerId, long stock) {
        ensureOfferState(scope, offerId, stock, 0.0d, 0.0d, 0L, 0L, null);
    }

    private void ensureOfferState(String scope, String offerId, long stock, double drainRate, double refillRate,
            long targetStock, long stockLimit, ShopOffer offer) {
        long now = System.currentTimeMillis();
        try (PreparedStatement state = connection.prepareStatement("""
                INSERT INTO shop_offer_economy_state(
                    scope, offer_id, stock, target_stock, stock_limit, drain_rate, refill_rate, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(scope, offer_id) DO NOTHING;
                """);
                PreparedStatement stats = connection.prepareStatement("""
                INSERT INTO shop_offer_trade_stats(scope, offer_id, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(scope, offer_id) DO NOTHING;
                """)) {
            state.setString(1, scope);
            state.setString(2, offerId);
            state.setLong(3, stock);
            state.setLong(4, Math.max(0L, targetStock));
            state.setLong(5, Math.max(0L, stockLimit));
            state.setDouble(6, Math.max(0.0d, drainRate));
            state.setDouble(7, Math.max(0.0d, refillRate));
            state.setLong(8, now);
            state.executeUpdate();

            stats.setString(1, scope);
            stats.setString(2, offerId);
            stats.setLong(3, now);
            stats.executeUpdate();
        } catch (SQLException ex) {
            Shop.logger().error("Could not reconcile shop economy state for " + scope + "/" + offerId + ": "
                    + ex.getMessage());
        }
    }

    private void migrateEconomyStateColumns() throws SQLException {
        if (!hasColumn("shop_offer_economy_state", "target_stock")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE shop_offer_economy_state ADD COLUMN target_stock BIGINT NOT NULL DEFAULT 0;");
            }
        }
        if (!hasColumn("shop_offer_economy_state", "stock_limit")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE shop_offer_economy_state ADD COLUMN stock_limit BIGINT NOT NULL DEFAULT 0;");
            }
        }
        addColumnIfMissing("shop_offer_economy_state", "stock_mode", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing("shop_offer_economy_state", "min_price_multiplier", "REAL NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "max_price_multiplier", "REAL NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "spread_percent", "REAL NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "drain_percent", "REAL NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "drain_max", "BIGINT NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "restock_percent", "REAL NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "restock_max", "BIGINT NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "per_player_daily_sell_limit", "BIGINT NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "global_daily_sell_limit", "BIGINT NOT NULL DEFAULT -1");
        addColumnIfMissing("shop_offer_economy_state", "economy_configured", "INTEGER NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!hasColumn(table, column)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition + ";");
            }
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ");");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String scope(ShopZone zone) {
        return "area:" + zone.getAreaId();
    }

    public static String scopeFor(ShopZone zone) {
        return zone == null || zone.getAreaId() <= 0L ? GLOBAL_SCOPE : scope(zone);
    }

    private static long dayStart(long now) {
        return Math.floorDiv(Math.max(0L, now), 86_400_000L) * 86_400_000L;
    }

    private static String playerKey(long playerId) {
        return "player:" + playerId;
    }

}
