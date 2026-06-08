# Roadmap Plan 04 Dynamic Economy Gap Plan

## Objective
Complete the Shop dynamic economy work that remains after the Plan 03 export phase and the Plan 04 persistence foundation phase.

This plan covers only `rw-plugin-oz-shop` behavior from:
- `local.res/roadmap-plan-03.md`
- `local.res/roadmap-plan-04.md`
- `local.res/roadmap-plan-shop-01.md`
- `docs/roadmaps/roadmap-plan-03-execution.md`
- `docs/roadmaps/roadmap-plan-04-execution.md`

## Ownership
Owning repository/plugin: `rw-plugin-oz-shop`

Supporting repositories/plugins:
- `rw-plugin-oz-wallet` for Wallet deposits, withdrawals, refund/rollback behavior, and currency defaults.
- `rw-plugin-oz-tools` for existing settings, SQLite, and shortcut infrastructure only.

## Current Implemented Baseline
- Item icons, generated offer export, and generated recipe export exist from Plan 03.
- `dynamicEconomyEnabled=false` exists as the default safety gate.
- Shop zones, per-zone offer-file selection, economy state, trade stats, stock, target stock, stock limit, drain/refill rates, and tick timestamps are persisted in world-scoped SQLite.
- System-shop quantity buy and sell commands exist.
- UI quantity input exists for selected system offers.
- Player purchase prices round up from configured `basePrice * amount`.
- Player sale payouts round down from configured `basePrice * amount`.
- Positive stock limits cap player purchases from the system shop.
- Lazy drain/refill ticks exist as absolute units per real-time hour.
- Admins can configure scoped stock and drain/refill rates with `/shop stock`.
- Admins can configure scoped stock mode, target/max stock, dynamic price settings, target tick settings, and sell limits with `/shop economy`.

## Resolved Implementation Scope
- Stock modes, dynamic stock-ratio pricing, enforced buy/sell spread, and max-stock rejection are implemented.
- Per-player/global sell limits are persisted, enforced, and visible in admin/UI diagnostics.
- Target-stock percentage/max ticks are implemented for `SYSTEM_SUPPLIED` and `HYBRID`; legacy absolute hourly rates remain loader-compatible.
- UI/admin diagnostics expose dynamic prices, stock mode, sell-limit status, and economy-disabled reasons.
- Runtime smoke for the complete dynamic economy loop remains open.

## Dependencies
- Runtime: compatible `rw-plugin-oz-tools` with `SQLiteConnectionFactory`, `PluginShortcutVisibility`.
- Runtime: compatible `rw-plugin-oz-wallet`; missing Wallet behavior must remain disabled with clear errors.
- Data: existing `shop_offer_economy_state` and `shop_offer_trade_stats` tables must remain migration-compatible.
- Config: `dynamicEconomyEnabled=false` remains the default until server admins explicitly opt in.

## Risks
- Player sell payout must be guarded before items are removed and before Wallet deposit. Otherwise stock-limit and sell-limit failures can become currency exploits.
- Buy/sell spread, rounding, and dynamic multipliers must be applied consistently to avoid arbitrage loops.
- New stock-mode fields must be additive and preserve existing offer JSON files.
- Economy ticks must not create cross-world state or depend on wall-clock assumptions that make tests flaky.
- UI must distinguish static disabled offers from economy-disabled offers so admins can diagnose configuration issues.

## Implementation Checklist
- [x] Add additive system-offer fields for `stockMode`, dynamic price min/max multipliers, and spread percent.
- [x] Add additive system-offer fields for drain/restock percent, drain/restock max, per-player sell limit, and global sell limit.
- [x] Add SQLite migration columns or companion table entries for any economy fields that must be runtime-scoped instead of JSON-configured.
- [x] Implement stock-mode tick rules: `STATIC`, `PLAYER_SUPPLIED`, `SYSTEM_SUPPLIED`, and `HYBRID`.
- [x] Replace absolute drain/refill ticks with the reference target-stock percent/max model while keeping legacy JSON compatibility in the loader.
- [x] Implement dynamic price resolution from `stock / targetStock` with configured min/max multipliers.
- [x] Enforce a nonzero buy/sell spread when dynamic economy is enabled so player sale payout is always lower than player purchase price for the same stock state.
- [x] Pre-check player sales against max stock/stock limit before inventory removal or Wallet payout.
- [x] Add per-player and global daily sell counters using persisted trade-stat state or an additive counter table.
- [x] Reject player sales when per-player/global limits are reached, with a localized message.
- [x] Make purchase stock checks quantity-aware by checking the full requested amount, not only one offer package.
- [x] Record stock and trade-stat updates after successful Wallet operations only, and keep rollback behavior explicit for failed callbacks/deposits.
- [x] Extend `/shop stock` or add focused admin commands for stock mode, target/max stock, dynamic price/spread settings, and sell limits.
- [x] Update system-shop UI cards and selected-offer panel with dynamic prices and stock cap rejection messages.
- [x] Update system-shop UI cards and selected-offer panel with stock mode, sell-limit status, and detailed economy-disabled reasons.
- [x] Add focused unit tests for price calculation, stock cap rejection, sell-limit rejection, tick behavior, quantity stock checks, and legacy JSON compatibility.
- [x] Update README, HISTORY, and roadmap progress notes after the first dynamic-pricing/exploit-protection slice.
- [x] Update README, HISTORY, and roadmap progress notes after remaining stock-mode and sell-limit work.

## Validation Strategy
- [x] `mvn -B test -f rw-plugin-oz-shop/pom.xml`
- [x] `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`
- [x] Direct Java compile to `/tmp/oz-shop-classes` while project `target` is blocked by stale `nobody:nogroup` build artifacts.
- [x] Runtime smoke with `dynamicEconomyEnabled=false`: static buy/sell behavior remains compatible.
- [x] Runtime smoke with `dynamicEconomyEnabled=true`: quantity buy, quantity sell, dynamic price movement, stock caps, sell limits, drain/restock ticks, per-zone offer files, and missing Wallet failures.

## Progress Notes
- Shop loaded without startup exceptions on the running `strato.V80` development server on 2026-06-08. The active-world SQLite schema contains the expected zone, economy-state, trade-stat, daily sell-counter, and scoped economy override migrations.
- Player testing subsequently confirmed Shop works as expected.
- Added eight focused JUnit tests covering static/dynamic pricing and spread, stepped bulk pricing, stock-mode sell capability, automatic tick modes, target tick amount rules, quantity-aware stock caps, persisted sell limits, and legacy activation flags. `mvn -B test` passed on 2026-06-08.
- Implemented first runtime slice: optional `stockMode`, `minPriceMultiplier`, `maxPriceMultiplier`, and `spreadPercent` fields are read from system-offer JSON and preserved on generated exports.
- Dynamic economy now prices system offers from `stock / targetStock` with configured min/max multipliers when `dynamicEconomyEnabled=true`.
- Dynamic economy enforces a minimum spread so player sell payouts stay below player purchase prices for the same stock state.
- Quantity buy stock checks now use the full requested package amount.
- Player sales are rejected before inventory removal and Wallet payout when adding the sold amount would exceed positive `stockLimit`.
- Shop UI price labels now use the dynamic price calculation for system offers.
- Added optional `drainPercent`, `drainMax`, `restockPercent`, `restockMax`, `perPlayerDailySellLimit`, and `globalDailySellLimit` system-offer fields.
- Added `shop_offer_daily_sell_counters` as an additive SQLite table and reject player sales before inventory removal/Wallet payout when per-player or global daily sell limits would be exceeded.
- Validation passed after the sell-limit slice with `mvn -B test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Added stock-mode tick semantics: `STATIC` and `PLAYER_SUPPLIED` do not run automatic ticks; `SYSTEM_SUPPLIED` and `HYBRID` run automatic drain/restock.
- Added target-stock percentage/max drain/restock tick behavior; legacy `drainRate` and `refillRate` remain loader-compatible for old files but are no longer emitted in default offers or exports.
- Validation passed after the stock-mode/tick slice with `mvn -B clean test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- System-shop cards and the selected-offer panel now show stock mode and daily sell-limit status.
- Validation passed after the UI economy-status slice with `mvn -B clean test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Selected-offer details now show localized economy block reasons for dynamic stock, max-stock, and daily sell-limit rules.
- Validation passed after the UI economy-block-reason slice with `mvn -B clean test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Added scoped SQLite runtime override columns for stock mode, dynamic price settings, target tick settings, and daily sell limits.
- Added `/shop economy <offer-id> key=value...` for scoped admin configuration of mode, target/max stock, dynamic price/spread settings, target ticks, and sell limits.
- Validation passed after the admin economy-override slice with `mvn -B clean test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Synced `src/system-offers.default.json` so every default offer carries current dynamic-economy JSON fields.
- Existing economy rows with no explicit `/shop economy` override now backfill missing `target_stock` and `stock_limit` from offer defaults during reconcile, fixing global stock displays that incorrectly appeared unlimited.

## Affected Repositories/Plugins
- `rw-plugin-oz-shop`
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep all new behavior gated by `dynamicEconomyEnabled`. Additive JSON fields and SQLite columns must be ignored safely by older static behavior. If dynamic economy causes runtime issues, admins should be able to disable it without losing existing static offers, zones, or stock records.
