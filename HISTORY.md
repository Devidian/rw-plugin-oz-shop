# History / Changelog / Commitlog

<https://www.conventionalcommits.org/en/v1.0.0/>

## [0.3.6] - 2026-07-20 | Focused system-shop refresh

- fix: recognize hybrid Anvil inventory items when selling to the system shop
- fix: update only the selected offer and its detail area after a system-shop trade

## [0.3.5] - 2026-07-20 | Advanced button controls

- change: use the stable shared OZ button controls in Shop overlays

## [0.3.4] - 2026-07-20 | Settings translations

- fix: localize all registered Shop admin settings

## [0.3.3] - 2026-07-20 | Update metadata

- change: publish the canonical GitHub release source for OZ Tools update management

## [0.3.2] - 2026-07-17 | Durability-adjusted system buyback

- fix: calculate system-shop buyback payouts per removed item's durability and reject zero-durability items
- fix: restore durability, status and modifier when a failed Wallet deposit rolls back a shop buyback

## [0.3.1] - 2026-07-17 | Configurable stock reconciliation

- feat: make the automatic stock reconciliation interval configurable in hours, defaulting to one hour
- fix: retain fractional drain/restock time until a whole-item stock change occurs

## [0.3.0] - 2026-07-15 | Hourly stock replenishment

- fix: apply configured restock percentages per elapsed hourly reconciliation step
- fix: enforce `restockMax` as a per-hour cap while reconciling overdue stock

## [0.2.3] - 2026-07-14 | Icon set and export polish

- change: rename Shop icon keys to their final semantic names
- feat: add route-ready shop-zone export DTOs, service, and exposure setting for manager bridges

## [0.2.1] - 2026-06-13 | Sword offer classification

- fix: classify and enable the common `sword2` system offer

## [0.2.0] - 2026-06-08 | Dynamic economy and shop zones

- fix: close the radial menu before building and opening the Shop overlay
- fix: mark unresolved constructionitem and clothingitem offers invalid and disabled
- build: generate the runtime system-offers.default.json from the complete offer catalog without invalid or unclassified offers
- perf: preload active system-offer icons during server startup
- change: classify invalid system offers as disabled static goods and exclude them from tier splits
- change: classify volcanic rock and sandstone as T1 basic stone resources
- change: enable all tradeable classified default system offers and price unclassified offers at 500
- build: split default system offers into always-enabled per-tier JSON files for distribution
- change: classify safe default-offer food, animal-product, small-animal, seed, and sapling families and apply tier stock defaults
- change: standardize comparable fish and meat prices by raw, cooked, dried, and burned processing state
- fix: keep system-shop stock updates, stock rules, and ticks active when dynamic stock pricing is disabled
- fix: make system-offer JSON definitions authoritative on reload while preserving DB runtime stock and counters only
- change: deprecate `/shop economy` DB definition overrides; admins should edit JSON and run `/shop reload`
- change: replace default system-offer `sellEnabled`/`buyEnabled` output with `isEnabled` and derive buy/sell capability from `stockMode`
- fix: treat `STATIC` system-shop stock as unlimited and ignore sell limits for modes that do not accept player sales
- fix: apply an effective minimum restock for unconfigured `SYSTEM_SUPPLIED` offers
- docs: add the default system-offer conversion audit under `docs/active/`
- feat: add admin-only current Shop-Zone overlay tab for area marking, zone naming, offer-file selection, systemshop mode, zone removal, and scoped stock reset
- fix: price dynamic-economy bulk system-shop trades as stepped per-item stock changes instead of one bulk multiplier
- fix: disable selected system-shop buy and sell actions for zero quantity and unavailable trades
- change: show system-shop card item names without quantity prefixes and locale-aware dynamic card unit prices with three decimals
- change: color Shop action buttons by economy meaning and avoid disabled-state hover geometry issues
- fix: use German umlauts in Shop economy labels
- fix: keep selected-offer sell button position stable when toggling disabled state
- feat: show selected system-offer buy and payout previews for the entered amount
- fix: prevent partial item removal when a system-shop sell amount exceeds matching inventory
- fix: disable selected-offer sell action when the entered amount exceeds matching inventory
- change: remove background and border from compact Shop Wallet balance chips
- fix: move Shop Wallet balances above the full overlay panel and compact the selected-offer admin controls
- fix: preview selected system-offer sell payouts for the currently entered amount
- feat: show compact Wallet currency balance chips above the Shop overlay body
- feat: add admin-only selected system-offer economy diagnostics and target-stock reset action
- change: remove redundant system-offer card select text while preserving selected-card styling
- change: require OZTools 0.21.0 for shared shortcut visibility
- feat: add a Shop overlay Sell tab for system-shop buyback offers
- feat: read default stock, drain, and refill values from system-offer JSON
- feat: persist shop zones, per-zone offer-file selection, stock, drain/refill rates, and trade stats in world-scoped SQLite
- feat: add `/shop buy <offer-id> [quantity]`, `/shop sell <offer-id> [quantity]`, `/shop zoneoffers`, `/shop stock`, and `/shop economy`
- feat: add central `systemShopCurrency` and omit legacy per-offer currency/price fields from generated default offers
- feat: add stock-based dynamic system-offer prices with min/max multipliers and enforced buy/sell spread
- fix: reject player system-shop sales before payout when the scoped stock limit would be exceeded
- fix: check dynamic system-shop stock against the full requested quantity
- feat: add additive dynamic-economy offer fields for target tick placeholders and daily sell limits
- feat: persist and enforce per-player and global daily system-shop sell limits before inventory removal and Wallet payout
- feat: apply automatic economy ticks only for `SYSTEM_SUPPLIED` and `HYBRID` stock modes
- feat: add target-stock percent/max drain and restock tick behavior with legacy hourly rates as fallback
- feat: show stock mode and daily sell-limit status on system-shop cards and selected-offer details
- feat: add system-shop offer search and keep the offer list position stable when selecting an offer
- fix: localize stock-mode labels in the Shop overlay
- fix: resolve object-kit variants, including large object kits, to concrete object names in generated exports
- change: replace default system offers with the T7/T11 base-economy offer set and reprice remaining export-default `basePrice=100` placeholders
- docs: add Shop economy classification and raw material pricing reference docs
- feat: show selected-offer economy block reasons for dynamic stock and sell-limit rules
- feat: persist scoped economy overrides for stock mode, target/max stock, dynamic price settings, target ticks, and sell limits
- fix: backfill scoped target stock and stock limits from offer defaults for existing economy rows that have no explicit economy override
- fix: synchronize default system offers with all current dynamic-economy JSON fields
- change: remove deprecated `drainRate` and `refillRate` from default system offers and generated exports
- fix: preserve default daily sell limits when system offers are reloaded and backfill unconfigured economy rows
- feat: resolve generic construction, clothing, and best-effort plant item exports to concrete definition names
- feat: let admins open the Shop radial management menu from shared shortcuts while normal players open the direct shop overlay
- feat: add player setting to hide the Shop shortcut from `/ozt` and the inventory shortcut panel
- change: remove obsolete shared escape-close registration pending future API support
- docs: update Shop Plan 04 persistence and dynamic economy documentation

## [0.1.0] - 2026-05-26

- feat: seed first-release system offers from generated item definitions and recipe-based baseline prices
- feat: add system-offer `basePrice`, `buyPrice`, `sellPrice`, `sellEnabled`, and `buyEnabled` fields
- change: price system offers as single-item packages with rounded buy and sell prices
- change: show capitalized item names instead of ids in Shop table, card, and command-list views
- fix: use a Shop-specific shop-zone shared-indicator icon key
- change: use the dedicated shop-zone shared-indicator icon for shop-zone signals
- feat: add admin-controlled item and recipe definition exports for Shop planning
- feat: add a reserved dynamic-economy enable switch defaulting to disabled
- feat: add Shop radial menu Info/Status entry using the shared Tools info icon
- fix: display system-offer item icons from Rising World item definitions when available
- feat: add shared Tools Info/Status panel content for Shop and route info/status commands to it
- feat: group and localize Shop admin settings metadata
- refactor: route Shop settings logging through the main `OZ.Shop` logger
- feat: add default card layout for Shop offers with a per-player list/card preference
- feat: move the shop-zone HUD signal into the shared Tools indicator panel
- feat: require confirmation before removing shop-area status
- fix: hide the create-shop-area radial action while already inside a shop area
- feat: create `OZ - Shop` plugin from the Maven template
- feat: add plugin offer registration and synchronous purchase callback API
- feat: add Wallet-backed purchase execution with callback-failure refund attempt
- feat: add admin-editable JSON system-shop offer loading
- feat: add `/shop` list, buy, and admin reload command workflow
- feat: change system offers to `itemName` and `itemVariant` based built-in item offers
- feat: add system offer `amount` for multi-item packages
- feat: generate `system-offer-example.json` from Rising World item definitions
- feat: support per-player dynamic plugin offer prices through `ShopPriceResolver`
- feat: add public shop-offer category and source metadata for plugin registrations
- feat: expose runtime plugin-offer registry listing and bulk unregister API
- feat: report callback-failure refunds and failed compensation during purchases
- feat: add shop availability settings and area-based shop access control
- feat: add admin radial action to mark the current existing area as a shop area
- feat: add shop UI with Systemshop, Pluginshop, and admin shop-area tabs
- feat: add global system-shop enablement and per-shop-area `systemShop` overrides
- feat: add shop-zone HUD indicator below LandClaim area info
- docs: document Shop API, install/update scope, and example plugin registration
- fix: send configured welcome message to all players instead of admins only
