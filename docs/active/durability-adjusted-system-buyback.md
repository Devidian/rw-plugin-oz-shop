# Durability-adjusted system buyback

## Objective

Pay system-shop buyback prices according to the actual durability of the
inventory items removed from the player, modifier quality, and require an
explicit player confirmation for condition-adjusted sales.

## Constraints

- Shop owns the offer, inventory and Wallet orchestration; no public sibling
  plugin API changes are required.
- Each selected item is priced independently using the dynamic unit buyback
  price and floored to whole Wallet units.
- Items with zero durability are not selected or removed. Non-durable items
  retain their full buyback value.
- `Normal` is 100%, `Broken` is 10%, and `Godly` is 1000%; intermediate
  modifiers scale monotonically between those values.
- The sell dialog must show the per-item durability/modifier payout breakdown
  before removing any condition-bearing items.
- A failed Wallet deposit restores the same slots with durability, status and
  modifier preserved.

## Validation

- [x] `mvn -B test` in a writable temporary checkout: 15 tests passed.
- [x] Development-runtime check: mixed durability/modifier stack,
  confirmation cancel/confirm behavior, zero-durability item, and forced
  Wallet failure rollback; accepted in-game on 2026-09-04.
