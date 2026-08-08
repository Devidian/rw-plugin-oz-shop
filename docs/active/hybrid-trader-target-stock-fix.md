# Hybrid trader target-stock fix

## Objective

Prevent automatic economy reconciliation from draining a hybrid NPC trader when its stock already equals its target.

## Ownership and dependencies

- Owner: `rw-plugin-oz-shop`
- Runtime dependency: Wallet is used only for stock movements that remain after the corrected stock calculation.
- No data migration or sibling-plugin API change.

## Plan

- [x] Trace the NPC-trader reconciliation and confirm that HYBRID drain used total stock.
- [x] Restrict HYBRID drain to the positive stock surplus above target.
- [x] Add focused coverage for at-target, surplus, and non-hybrid behavior.
- [x] Run Shop tests, API verification, and package build.
- [ ] Perform a development-server trader economy smoke test.

## Risk and rollback

Only automatic hybrid drain changes. Trader stock at target now remains stable; stock above target continues to drain, and stock below target can restock. Revert the Shop artifact to restore the former behavior.
