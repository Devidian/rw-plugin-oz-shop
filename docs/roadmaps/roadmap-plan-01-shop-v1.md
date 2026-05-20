# Roadmap Plan 01 Shop V1

## Objective

Provide the shared Shop plugin before feature plugins register purchasable GPS, LandClaim, or other actions.

## Ownership

Primary repository: `rw-plugin-oz-shop`.

Supporting repositories:
- `rw-plugin-oz-tools` provides shared settings, i18n, logging, and menu helpers.
- `rw-plugin-oz-wallet` owns balances and transaction execution.

## Dependencies

- `rw-plugin-oz-tools` is a hard runtime dependency.
- `rw-plugin-oz-wallet` is required for functional purchases.
- Feature plugins own their purchase callbacks and business rules.

## Implementation Checklist

- [x] Create repository from `rw-plugin-maven-template`.
- [x] Add public offer registration API.
- [x] Add synchronous purchase callback contract.
- [x] Add Wallet-backed purchase execution with refund attempt on callback failure.
- [x] Add admin-editable JSON system-shop item offer file.
- [x] Generate `system-offer-example.json` from Rising World item definitions.
- [x] Add `/shop` list, buy, and admin reload command workflow.
- [x] Update README, HISTORY, and root catalog docs.

## Risks

- Callback failures after Wallet withdrawal require compensation through a refund transaction.
- System-shop offers need concrete purchase actions before they are functionally purchasable.
- Future UI and zone work should not duplicate Tools helpers.

## Validation Strategy

- Run API helper summary.
- Run Maven package and tests.
- Runtime smoke should cover Tools + Wallet + Shop installed together and Wallet missing.

## Affected Repositories/Plugins

- `rw-plugin-oz-shop`
- root `AGENTS.md`
- root `PLUGIN_CATALOG.md`
- root Roadmap Plan 01 execution tracker

## Rollback Considerations

Disable or remove offers from `system-offers.json` to stop system-shop availability. Feature plugins can unregister plugin offers without deleting Wallet transaction history.
