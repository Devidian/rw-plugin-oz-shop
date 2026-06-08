# Roadmap Plan 04 Dynamic Economy And Persistence

## Objective
Complete the next Shop economy phase by migrating runtime state to SQLite, simplifying offer schema, supporting quantity-based buy/sell flows, and enabling zone-specific dynamic economy behavior.

## Ownership
Primary repository: `rw-plugin-oz-shop`

Supporting repositories:
- `rw-plugin-oz-tools` for shared persistence, settings, UI, i18n, and shortcut behavior.
- `rw-plugin-oz-wallet` for payments, refunds, balances, and central currency behavior.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Functional runtime dependency: `rw-plugin-oz-wallet`.
- Plan 03 Phase 5 item/recipe export data should be inspected before dynamic economy defaults are finalized.
- Shop schema and runtime persistence changes need migration review before implementation.

## Phases
- [x] Phase 1: Run migration review for offer schema, shop-zone SQLite storage, stock SQLite storage, and zone-specific offer file selection.
- [x] Phase 2: Inspect generated Plan 03 item and recipe exports and record economy balancing assumptions.
- [x] Phase 3: Migrate shop zones from JSON to world-isolated SQLite storage, preserving existing JSON import/migration where possible.
- [x] Phase 4: Add SQLite-backed stock, trade stats, drain/refill rates, and last economy tick state.
- [x] Phase 5: Support optional per-zone offer JSON file selection, persisted in the new shop-zone database.
- [x] Phase 6: Change system-offer schema so editable offers contain `basePrice` as unit price, `amount` as common trade size, and no per-offer `currency`; currency is configured centrally.
- [x] Phase 7: Implement quantity input for player buys and sells.
- [x] Phase 8: Apply rounding rules: player purchases use rounded-up `basePrice * amount`; player sales use rounded-down `basePrice * amount`.
- [x] Phase 9: Implement dynamic economy stock modes, target-based drain/refill, dynamic pricing, buy/sell spread, sell limits, and exploit protections when enabled.
- [x] Phase 10: Open the Shop radial menu from `/ozt` and inventory entry for admins, while normal players may still open the direct shop view.
- [x] Phase 11: Add Plan 04 player shortcut visibility setting, document the Escape-close API limitation, verify i18n loading, and migration away from deprecated Tools `SQLite` usage if present.
- [x] Phase 12: Update README/HISTORY and validate.

## Risks
- Dynamic economy can create money loops if quantity, stock, rounding, buy/sell spread, and Wallet rollback are not handled atomically.
- Zone-specific offer files can produce inconsistent behavior if stock and rates are not bound to the same zone identity.
- Removing `currency` from offers requires a clear migration/defaulting path.
- Moving zones and stock from JSON to SQLite must preserve world isolation and avoid cross-world contamination.

## Progress Notes
- Shop zones now persist in the world-scoped SQLite database opened through `SQLiteConnectionFactory`.
- Existing `shop-zones.json` is imported once when the new `shop_zones` table is empty.
- New zone writes no longer update JSON; JSON is retained as an import source for migration validation.
- Shop no longer imports or constructs deprecated Tools `SQLite`.
- `shop_offer_economy_state` stores stock, drain/refill rates, and last tick state per global or area-scoped system offer.
- `shop_offer_trade_stats` stores bought/sold counters and values per global or area-scoped system offer.
- Successful system-shop purchases now increment scoped sold counters and values; dynamic stock limits remain disabled.
- `shop_zones.system_offers_file` is used by the system-shop listing and purchase path when players stand in that shop area.
- Admins can set the current shop area's system-offer file with `/shop zoneoffers <file>` and reset to the global default with `/shop zoneoffers default`.
- A richer admin UI control for per-zone offer files remains optional polish.
- `systemShopCurrency` is now the central currency override for all system-shop offer files.
- Legacy per-offer `currency` remains readable only when `systemShopCurrency` is empty, preserving existing JSON compatibility.
- Generated offer exports no longer include a per-offer `currency` field.
- `/shop buy <offer-id> [quantity]` now supports quantity purchases for system-shop offers.
- Quantity purchases multiply the delivered item amount, charged price, and scoped sold stats by the requested quantity.
- Plugin-registered offers still reject quantity values greater than one because plugin callbacks are not guaranteed to be repeatable.
- New-schema system offers derive player purchase price by rounding `basePrice * amount` up.
- Future player sales should derive player sale payout by rounding `basePrice * amount` down.
- Legacy explicit `buyPrice`/`sellPrice` fields remain readable as migration overrides, but generated/default offers now use only `basePrice`.
- `/shop sell <offer-id> [quantity]` now removes matching system-offer items from the player inventory, pays Wallet, and records scoped bought stats.
- Failed Wallet payouts attempt to return the removed items to the player inventory.
- When `dynamicEconomyEnabled=true`, system-shop purchases check scoped stock before selling to players.
- `stock=0` currently remains unlimited for migration safety; positive stock is enforced as a hard cap.
- Admins can configure scoped stock with `/shop stock <offer-id> <stock>`.
- The stock command targets the current shop area when the admin stands in one, otherwise the global scope.
- Drain/restock percent and max values are applied lazily against target stock during reload, stock checks, and buy/sell updates.
- Dynamic economy still avoids implicit spread pricing beyond the configured buy/sell rounding rules; admins control spread through `basePrice` and legacy migration overrides.
- Phase 9 implementation is complete: stock persistence, stock caps, quantity buy/sell, stock-ratio pricing, enforced spread, max-stock rejection, daily sell limits, stock-mode ticks, target-based drain/restock, admin controls, UI diagnostics, and focused unit tests exist. Live runtime smoke remains tracked in `rw-plugin-oz-shop/docs/active/roadmap-plan-04-dynamic-economy-gap-plan.md`.
- Shop shortcut entry now opens the admin radial menu for admins and opens the direct shop overlay for normal players.
- Validation after the shortcut entry update passed with `mvn -B test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Shop now depends on `rw-plugin-oz-tools` 0.21.0 for `PluginShortcutVisibility`.
- Players can hide the Shop shortcut from `/ozt` and the inventory shortcut panel in Shop player settings.
- Custom-overlay Escape behavior is deferred to the future Rising World API layer.
- Validation after the runtime-standard update passed with `mvn -B test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- README and HISTORY now document the Plan 04 persistence, economy, shortcut, and Tools 0.21.0 update scope.
- Final Shop validation passed with `mvn -B test -f rw-plugin-oz-shop/pom.xml` and `mvn -B -DskipTests package -f rw-plugin-oz-shop/pom.xml`.
- Phase 1 is complete: migration review is recorded in root `docs/active/roadmap-plan-04-step-01-architecture-migration-review.md` and gated Shop offer schema, shop-zone SQLite storage, stock SQLite storage, and zone-specific offer file selection before implementation.
- Phase 2 is complete using `local.res/system-offer-export.json` and `local.res/system-recipes-export.json`.
- Export inspection found 6,378 generated offer references and 658 generated recipes. The offer export is a pre-Plan-04 reference shape with `price`, `currency`, and `enabled=false` on every entry; generated offers are therefore useful as item coverage data but not as final editable economy defaults.
- Recipe inspection found 655 recipes with ingredients, 637 recipes with crafting stations, 313 variant-capable recipes, and output amounts up to 4. Recipe categories are broad and skew heavily toward Build and Furnishings, so stock/rate defaults should stay admin-configured instead of inferred globally.
- Current balancing assumptions after export inspection: `dynamicEconomyEnabled=false` remains the default, `stock=0` remains unlimited for migration safety, positive stock is an explicit hard cap, drain/refill values remain per-offer/per-zone admin settings, and admins should choose buy/sell spread through `basePrice` or legacy migration overrides instead of automatic recipe-derived pricing.

## Validation Strategy
- Run `scripts/verify-plugin-api.sh --summary` if API checks remain available.
- Run `mvn -B test` and `mvn -B -DskipTests package`.
- Runtime-smoke offer schema migration, quantity buy/sell, rounding, central currency, missing Wallet behavior, per-zone offer files, per-zone stock/rates, daily tick, JSON-to-SQLite migration, shortcut visibility, and explicit close controls.

## Affected Repositories/Plugins
- `rw-plugin-oz-shop`
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep dynamic economy behind an admin setting. Preserve old offer/zone JSON as an import source until SQLite migration has been verified in a real server world.
