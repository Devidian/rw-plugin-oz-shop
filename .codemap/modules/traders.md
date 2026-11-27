# Traders, economy and exports
<!-- codemap-id: rw-plugin-oz-shop/traders -->

## Responsibility

Manage system offer files, trader state, dynamic economy, zones and export routes.

## Source and tests

- [`TraderService`](../../src/de/omegazirkel/risingworld/shop/TraderService.java) — trader management.
- [`TraderAppearance`](../../src/de/omegazirkel/risingworld/shop/TraderAppearance.java) — generated visual attributes, clothing application and appearance checks. [Seeded tests](../../src-test/de/omegazirkel/risingworld/shop/TraderAppearanceTest.java) characterize selection.
- [`ShopEconomyStore`](../../src/de/omegazirkel/risingworld/shop/ShopEconomyStore.java) — economy persistence.
- [`SystemOfferFile`](../../src/de/omegazirkel/risingworld/shop/SystemOfferFile.java) — system offer input. The local [flat object parser](../../src/de/omegazirkel/risingworld/shop/JsonObjects.java) retains the established zone and offer formats. [Compatibility tests](../../src-test/de/omegazirkel/risingworld/shop/JsonObjectsCompatibilityTest.java) cover their distinct rules.
- [`ShopOverlay`](../../src/de/omegazirkel/risingworld/shop/ui/ShopOverlay.java) — shop UI.
- [`ShopTraderRoute`](../../src/de/omegazirkel/risingworld/shop/web/ShopTraderRoute.java) — trader export route.
- [Tests](../../src-test/de/omegazirkel/risingworld/shop/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).

## Dependencies

- `rw-plugin-oz-player-factions/factions` — optional faction trader integration. [Integration source](../../src/de/omegazirkel/risingworld/shop/FactionTraderBridge.java)
