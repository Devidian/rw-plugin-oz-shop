# rw-plugin-oz-shop
<!-- codemap-id: rw-plugin-oz-shop -->

## Purpose

System and plugin offers, trader interactions and Wallet-backed purchase orchestration. [Descriptor](../src/resources/plugin.yml) names [`Shop`](../src/de/omegazirkel/risingworld/Shop.java); [`ShopRuntime`](../src/de/omegazirkel/risingworld/ShopRuntime.java) handles lifecycle and dispatch.

## Modules

- [Offer and purchase API](modules/api.md).
- [Traders, economy and exports](modules/traders.md).

## Build and tests

[Maven](../pom.xml) builds against the Rising World API and OZ Tools. Explore [tests](../src-test/de/omegazirkel/risingworld/shop/) and follow [repository instructions](../AGENTS.md) for validation.

## Dependencies

- `rw-plugin-oz-tools/shared-api` — build and shared runtime infrastructure. [Build configuration](../pom.xml)
