# Durability-adjusted system buyback

## Objective

Pay system-shop buyback prices according to the actual durability of the
inventory items removed from the player.

## Constraints

- Shop owns the offer, inventory and Wallet orchestration; no public sibling
  plugin API changes are required.
- Each selected item is priced independently using the dynamic unit buyback
  price and floored to whole Wallet units.
- Items with zero durability are not selected or removed. Non-durable items
  retain their full buyback value.
- A failed Wallet deposit restores the same slots with durability, status and
  modifier preserved.

## Validation

- [x] `mvn -B test` in a writable temporary checkout: 13 tests passed.
- [ ] Development-runtime check: mixed durability stack, zero-durability item,
  and forced Wallet failure rollback.
