# Roadmap Plan 03 Shop Export And Dynamic Economy

## Objective
Fix item icon display, add admin-controlled item/recipe exports, and adapt `local.res/roadmap-plan-shop-01.md` into an optional, admin-switchable dynamic economy extension for the current Shop plugin.

## Ownership
Primary repository: `rw-plugin-oz-shop`

Supporting repositories:
- `rw-plugin-oz-wallet` for payments, refunds, balances, and currencies.
- `rw-plugin-oz-tools` for shared UI/settings/persistence helpers.

## Dependencies
- Hard runtime dependency: `rw-plugin-oz-tools`.
- Functional runtime dependency: `rw-plugin-oz-wallet`.
- Dynamic economy must be disabled by default or behind explicit admin settings until configured.
- Export settings and economy persistence/config shape need migration review before implementation.

## Phases
- [x] Phase 1: Fix system/item offer icon display by using item-definition or variant `getIcon` data where available, falling back to existing shop icons.
- [x] Phase 2: Add a `settings.properties` admin switch controlling whether game-definition export files are generated.
- [x] Phase 3: Rename generated `system-offer-example.json` behavior to `system-offer-export.json` and preserve update guidance for existing admins.
- [x] Phase 4: Add `system-recipes-export.json` generation from extracted crafting recipes for later price calculation work.
- [ ] Phase 5: Run and inspect the new recipe export before choosing dynamic-economy balancing defaults, because recipe usage frequency and crafting inputs should inform stock, price, and limit defaults.
- [ ] Phase 6: Adapt the dynamic economy concept into current Shop terms: stock modes, target/max stock, daily drain/restock, dynamic buy/sell prices, buy/sell spread, sell limits, and exploit protections.
- [ ] Phase 7: Define persistence for stock, trade stats, sell counters, and last economy tick.
- [ ] Phase 8: Implement optional player selling to the system only when dynamic economy is enabled and item config permits it.
- [ ] Phase 9: Add UI/admin indicators for dynamic price, stock, sell availability, and disabled economy fallback.
- [x] Phase 10: Add a radial-menu Info/Status button in the Shop main menu.
- [x] Phase 11: Update README/HISTORY and validate.

## Risks
- Dynamic economy can create money loops if buy/sell spread and stock limits are not enforced consistently.
- Recipe export depends on available Rising World API definitions and may need graceful partial output.
- Renaming export files can confuse existing admins unless documentation clearly separates editable offer files from generated export files.
- Stock persistence and daily ticks are migration-sensitive.

## Validation Strategy
- Run `scripts/verify-plugin-api.sh --summary` plus targeted checks for item-definition, icon, and recipe APIs.
- Run `mvn -B -DskipTests package`.
- Run `mvn -B test`.
- Runtime-smoke export disabled/enabled, icon rendering, recipe export, dynamic economy disabled fallback, player sell limits, max-stock rejection, daily tick, Wallet payment/refund paths, and missing Wallet behavior.

## Affected Repositories/Plugins
- `rw-plugin-oz-shop`
- `rw-plugin-oz-wallet`
- `rw-plugin-oz-tools`

## Rollback Considerations
Keep generated exports and dynamic economy independent. Dynamic economy should remain disableable without breaking static system/plugin offers.

## Progress Notes
- Phase 1 complete: system-shop cards now use `Definitions.getItemDefinition(itemName).getIcon(itemVariant)` when possible and fall back to configured Shop/plugin icons.
- Phase 2 complete: `generateDefinitionExports=false` is available in settings and admin settings; exports are generated only when explicitly enabled.
- Phase 3 complete: generated item-offer references now use `system-offer-export.json`; existing `system-offer-example.json` files are not touched.
- Phase 4 complete: `system-recipes-export.json` can be generated from `Definitions.getAllRecipes()` with item, ingredient, station, category, and raw recipe metadata for later balancing.
- Phase 5 remains pending until the export has run once in a real server runtime and the resulting recipe frequency/input data has been inspected.
- Phase 6 is intentionally limited to a gate for now: `dynamicEconomyEnabled=false` is present as an explicit disabled-by-default switch, but no dynamic stock, price, sell, or persistence behavior is active yet.
- Phases 7-9 remain pending because stock persistence, sell flow, and UI indicators need defaults informed by the recipe export.
- Phase 10 complete: the Shop radial menu has an `Info / Status` entry using the Tools-registered `icon-ki-info-status` icon.
- Validation passed with `scripts/verify-plugin-api.sh --summary`, `mvn -B test`, and `mvn -B -DskipTests package`.
