# Offer and purchase API
<!-- codemap-id: rw-plugin-oz-shop/api -->

## Responsibility

Own public offer registration and purchase callbacks. Callbacks fulfill feature-specific offers in the registering plugin. Wallet owns balances.

## Source and tests

- [`Shop`](../../src/de/omegazirkel/risingworld/Shop.java) — public plugin facade.
- [`ShopRuntime`](../../src/de/omegazirkel/risingworld/ShopRuntime.java) — offer registration and purchase entry points.
- [`ShopService`](../../src/de/omegazirkel/risingworld/shop/ShopService.java) — purchase orchestration.
- [`ShopPurchaseCallback`](../../src/de/omegazirkel/risingworld/shop/ShopPurchaseCallback.java) — plugin callback contract.
- [`ShopPurchaseResult`](../../src/de/omegazirkel/risingworld/shop/ShopPurchaseResult.java) — purchase result contract.
- [Tests](../../src-test/de/omegazirkel/risingworld/shop/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).

## Dependencies

- `rw-plugin-oz-wallet/ledger` — runtime Wallet-backed purchases. [Purchase and settlement call sites](../../src/de/omegazirkel/risingworld/shop/ShopService.java)
