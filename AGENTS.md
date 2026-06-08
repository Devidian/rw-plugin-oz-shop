# AGENTS.md

## Repository Purpose

This repository owns the `OZ - Shop` Rising World plugin.

## Ownership

Owns:
- shared plugin offer registration API
- system-shop item offer file loading
- Wallet-backed purchase orchestration
- synchronous purchase callback execution
- lightweight `/shop` command workflow

Does not own:
- wallet balances, currencies, or transaction history
- feature-specific reward, teleport, claim, or marketplace business logic
- generic UI, settings, persistence, logging, or transport helpers

## Dependencies

- Hard runtime dependency: `rw-plugin-oz-tools`
- Functional runtime dependency: `rw-plugin-oz-wallet`

If `OZ - Wallet` is missing or not loaded, this plugin may load but purchases must not function and admins should be warned.

## Mandatory Workflow Rules

- Preserve the Java 20 baseline.
- Keep purchase orchestration in Shop and economy state in Wallet.
- Keep plugin-specific purchase fulfillment in the registering feature plugin callback.
- Use `rw-plugin-oz-tools` helpers for shared runtime concerns.
- Keep the complete system item catalog in `src/system-offers.complete.json`; generate the packaged `system-offers.default.json` with enabled classified T0-T11 offers only.
- Keep system item offers in JSON with `id`, `itemName`, `itemVariant`, `amount`, `basePrice`, `stockMode`, economy fields, and `isEnabled`; legacy `sellEnabled` and `buyEnabled` are deprecated compatibility input only.
- Copy `system-offers.default.json` on first run/update and generate `system-offer-example.json` from `Definitions.getAllItemDefinitions()` when missing.
- Treat public result objects, `ShopPurchaseCallback`, and main-class public API methods as sibling-plugin compatibility surface.
- Keep `README.md`, `HISTORY.md`, and `PLANS.md` aligned with behavior changes.

## Validation

- Run `mvn -B -DskipTests package` for build-impacting changes.
- Run `scripts/verify-plugin-api.sh --summary` plus targeted checks when adding or changing Rising World API calls.
- Run `mvn -B test` when tests exist.
